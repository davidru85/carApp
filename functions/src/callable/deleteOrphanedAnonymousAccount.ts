import {createHash, randomBytes} from "node:crypto";
import {logger as firebaseLogger} from "firebase-functions";
import {HttpsError, onCall} from "firebase-functions/v2/https";

import {
    type AuthUserProviderSnapshot,
    canIssueOrphanCleanupTicket,
    isAnonymousAuthUser,
} from "../auth/anonymousUserEligibility.js";
import {firebaseAdminDeletionGateways} from "../deletion/firebaseAdminDeletionGateways.js";
import {
    deleteUserData,
    type UserDataFirestoreGateway,
} from "../deletion/userDeletionService.js";

const ORPHAN_DELETED = "ORPHANED_ANONYMOUS_ACCOUNT_DELETED";
const ORPHAN_CLEANUP_TICKET_ISSUED = "ORPHAN_CLEANUP_TICKET_ISSUED";
const ORPHAN_CLEANUP_TICKET_LIFETIME_MS = 30 * 24 * 60 * 60 * 1000;
const CLEANUP_TICKET_PATTERN = /^[A-Za-z0-9_-]{43}$/;

interface OrphanCleanupPayload {
    cleanupTicket?: unknown;
}

interface OrphanCleanupRequest {
    auth?: {
        uid: string;
        token?: {
            firebase?: {
                sign_in_provider?: string;
                [key: string]: unknown;
            };
            [key: string]: unknown;
        };
    };
    data: unknown;
}

export interface OrphanCleanupAuthorizationRecord {
    anonymousUid: string;
    expiresAtMs: number;
    status: "PENDING" | "COMPLETED";
    ticketHash: string;
}

export interface OrphanCleanupAuthorizationGateway {
    complete(ticketHash: string): Promise<void>;
    get(ticketHash: string): Promise<OrphanCleanupAuthorizationRecord | null>;
    issue(authorization: OrphanCleanupAuthorizationRecord): Promise<void>;
}

interface OrphanCleanupLogger {
    error(message: string, context: {stage: "AUTHORIZATION" | "AUTH_USER" | "REMOTE_DATA"}): void;
    info(
        message: string,
        context: {
            status: typeof ORPHAN_CLEANUP_TICKET_ISSUED | typeof ORPHAN_DELETED;
        },
    ): void;
}

export interface OrphanCleanupTicketDependencies {
    auth: OrphanCleanupAuthGateway;
    authorizations: OrphanCleanupAuthorizationGateway;
    clock: {nowMs(): number};
    logger: OrphanCleanupLogger;
    ticketGenerator(): string;
}

export interface OrphanCleanupAuthGateway {
    deleteUser(uid: string): Promise<void>;
    getUser(uid: string): Promise<AuthUserProviderSnapshot | null>;
}

export interface OrphanCleanupDependencies {
    auth: OrphanCleanupAuthGateway;
    authorizations: OrphanCleanupAuthorizationGateway;
    clock: {nowMs(): number};
    firestore: UserDataFirestoreGateway;
    logger: OrphanCleanupLogger;
}

export function createOrphanCleanupTicketHandler(dependencies: OrphanCleanupTicketDependencies) {
    return async (request: OrphanCleanupRequest): Promise<{cleanupTicket: string}> => {
        const anonymousUid = request.auth?.uid;
        if (anonymousUid === undefined) {
            throw new HttpsError("unauthenticated", "Authentication is required");
        }
        if (request.auth?.token?.firebase?.sign_in_provider !== "anonymous") {
            throw new HttpsError(
                "failed-precondition",
                "The caller must be authenticated with an anonymous account",
            );
        }

        // The claim is only evidence of what was true when the token was minted. Callable
        // verification performs no revocation or current-user check, so the Admin record is the
        // only current answer to "is this still an anonymous account?" (`D-148`).
        let caller: AuthUserProviderSnapshot | null;
        try {
            caller = await dependencies.auth.getUser(anonymousUid);
        } catch {
            dependencies.logger.error("Orphan cleanup ticket issuance failed", {
                stage: "AUTH_USER",
            });
            throw new HttpsError("internal", "Orphan cleanup ticket issuance failed");
        }
        if (!canIssueOrphanCleanupTicket(caller)) {
            throw new HttpsError(
                "failed-precondition",
                "The caller is no longer an eligible anonymous account",
            );
        }

        const cleanupTicket = dependencies.ticketGenerator();
        const ticketHash = hashCleanupTicket(cleanupTicket);
        try {
            await dependencies.authorizations.issue({
                anonymousUid,
                expiresAtMs: dependencies.clock.nowMs() + ORPHAN_CLEANUP_TICKET_LIFETIME_MS,
                status: "PENDING",
                ticketHash,
            });
        } catch {
            dependencies.logger.error("Orphan cleanup ticket issuance failed", {
                stage: "AUTHORIZATION",
            });
            throw new HttpsError("internal", "Orphan cleanup ticket issuance failed");
        }

        dependencies.logger.info("Orphan cleanup ticket issued", {
            status: ORPHAN_CLEANUP_TICKET_ISSUED,
        });
        return {cleanupTicket};
    };
}

export function createOrphanCleanupHandler(dependencies: OrphanCleanupDependencies) {
    return async (request: OrphanCleanupRequest): Promise<{status: typeof ORPHAN_DELETED}> => {
        const callerUid = request.auth?.uid;
        if (callerUid === undefined) {
            throw new HttpsError("unauthenticated", "Authentication is required");
        }

        const callerProvider = request.auth?.token?.firebase?.sign_in_provider;
        if (callerProvider === undefined || callerProvider === "anonymous") {
            throw new HttpsError(
                "failed-precondition",
                "The caller must be authenticated with a permanent account",
            );
        }

        const cleanupTicket = readCleanupTicket(request.data);
        if (cleanupTicket === null) {
            throw new HttpsError("invalid-argument", "A valid cleanup ticket is required");
        }
        const ticketHash = hashCleanupTicket(cleanupTicket);

        let authorization: OrphanCleanupAuthorizationRecord | null;
        try {
            authorization = await dependencies.authorizations.get(ticketHash);
        } catch {
            dependencies.logger.error("Orphaned anonymous cleanup failed", {
                stage: "AUTHORIZATION",
            });
            throw new HttpsError("internal", "Orphaned anonymous cleanup failed");
        }

        if (authorization === null || authorization.expiresAtMs <= dependencies.clock.nowMs()) {
            throw new HttpsError("invalid-argument", "The cleanup ticket is not valid");
        }

        if (authorization.status === "COMPLETED") {
            return completedResponse(dependencies.logger);
        }

        const orphanUid = authorization.anonymousUid;
        if (orphanUid === callerUid) {
            throw new HttpsError(
                "failed-precondition",
                "The caller must not target the current account",
            );
        }

        let orphanUser: AuthUserProviderSnapshot | null;
        try {
            orphanUser = await dependencies.auth.getUser(orphanUid);
        } catch {
            dependencies.logger.error("Orphaned anonymous cleanup failed", {stage: "AUTH_USER"});
            throw new HttpsError("internal", "Orphaned anonymous cleanup failed");
        }
        if (orphanUser !== null && !isAnonymousAuthUser(orphanUser)) {
            throw new HttpsError(
                "failed-precondition",
                "The cleanup ticket no longer targets an anonymous account",
            );
        }

        try {
            if (orphanUser !== null) {
                await dependencies.auth.deleteUser(orphanUid);
            }
        } catch (failure) {
            if (!isMissingAuthUser(failure)) {
                dependencies.logger.error("Orphaned anonymous cleanup failed", {stage: "AUTH_USER"});
                throw new HttpsError("internal", "Orphaned anonymous cleanup failed");
            }
        }

        try {
            await deleteUserData({firestore: dependencies.firestore, uid: orphanUid});
        } catch {
            dependencies.logger.error("Orphaned anonymous cleanup failed", {stage: "REMOTE_DATA"});
            throw new HttpsError("internal", "Orphaned anonymous cleanup failed");
        }

        try {
            await dependencies.authorizations.complete(ticketHash);
        } catch {
            dependencies.logger.error("Orphaned anonymous cleanup failed", {
                stage: "AUTHORIZATION",
            });
            throw new HttpsError("internal", "Orphaned anonymous cleanup failed");
        }

        return completedResponse(dependencies.logger);
    };
}

export const issueOrphanCleanupTicket = onCall<Record<string, never>>(
    {
        maxInstances: 2,
        memory: "256MiB",
        region: "europe-west1",
        timeoutSeconds: 60,
    },
    async (request) => {
        const gateways = firebaseAdminDeletionGateways();
        const handler = createOrphanCleanupTicketHandler({
            auth: gateways.auth,
            authorizations: gateways.orphanCleanupAuthorizations,
            clock: {nowMs: Date.now},
            logger: firebaseLogger,
            ticketGenerator: () => randomBytes(32).toString("base64url"),
        });
        return handler(request);
    },
);

export const deleteOrphanedAnonymousAccount = onCall<OrphanCleanupPayload>(
    {
        maxInstances: 2,
        memory: "256MiB",
        region: "europe-west1",
        timeoutSeconds: 60,
    },
    async (request) => {
        const gateways = firebaseAdminDeletionGateways();
        const handler = createOrphanCleanupHandler({
            auth: gateways.auth,
            authorizations: gateways.orphanCleanupAuthorizations,
            clock: {nowMs: Date.now},
            firestore: gateways.firestore,
            logger: firebaseLogger,
        });
        return handler(request);
    },
);

function completedResponse(logger: OrphanCleanupLogger): {status: typeof ORPHAN_DELETED} {
    logger.info("Orphaned anonymous cleanup completed", {status: ORPHAN_DELETED});
    return {status: ORPHAN_DELETED};
}

function readCleanupTicket(data: unknown): string | null {
    if (data === null || typeof data !== "object") {
        return null;
    }
    const ticket = (data as OrphanCleanupPayload).cleanupTicket;
    return typeof ticket === "string" && CLEANUP_TICKET_PATTERN.test(ticket) ? ticket : null;
}

function hashCleanupTicket(ticket: string): string {
    return createHash("sha256").update(ticket, "utf8").digest("hex");
}

function isMissingAuthUser(failure: unknown): boolean {
    return failure !== null &&
        typeof failure === "object" &&
        "code" in failure &&
        failure.code === "auth/user-not-found";
}
