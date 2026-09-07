import {logger as firebaseLogger} from "firebase-functions";
import type {DecodedIdToken} from "firebase-admin/auth";
import {HttpsError, onCall} from "firebase-functions/v2/https";

import {firebaseAdminDeletionGateways} from "../deletion/firebaseAdminDeletionGateways.js";
import {
    deleteUserData,
    type UserDataFirestoreGateway,
} from "../deletion/userDeletionService.js";

const ORPHAN_DELETED = "ORPHANED_ANONYMOUS_ACCOUNT_DELETED";

interface OrphanCleanupPayload {
    anonymousIdToken?: unknown;
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

export type VerifiedIdentityToken = Pick<DecodedIdToken, "uid" | "firebase">;

export interface OrphanCleanupAuthGateway {
    deleteUser(uid: string): Promise<void>;
    getUser?(uid: string): Promise<{uid: string}>;
    verifyIdToken(token: string): Promise<VerifiedIdentityToken>;
}

interface OrphanCleanupLogger {
    error(message: string, context: {stage: "AUTH_USER" | "REMOTE_DATA"}): void;
    info(message: string, context: {status: typeof ORPHAN_DELETED}): void;
}

interface OrphanCleanupDependencies {
    auth: OrphanCleanupAuthGateway;
    firestore: UserDataFirestoreGateway;
    logger: OrphanCleanupLogger;
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

        const anonymousIdToken = readAnonymousIdToken(request.data);
        if (anonymousIdToken === null) {
            throw new HttpsError("invalid-argument", "A valid anonymous ID token is required");
        }

        const {skipAuthDeletion, verified} = await resolveCapturedIdentity(
            dependencies,
            anonymousIdToken,
        );

        if (verified.firebase?.sign_in_provider !== "anonymous") {
            throw new HttpsError(
                "failed-precondition",
                "The captured identity is not an anonymous account",
            );
        }

        const orphanUid = verified.uid;
        if (orphanUid === callerUid) {
            throw new HttpsError(
                "failed-precondition",
                "The caller must not target the current account",
            );
        }

        if (!skipAuthDeletion) {
            try {
                await dependencies.auth.deleteUser(orphanUid);
            } catch (failure) {
                if (!isMissingAuthUser(failure)) {
                    dependencies.logger.error("Orphaned anonymous cleanup failed", {stage: "AUTH_USER"});
                    throw new HttpsError("internal", "Orphaned anonymous cleanup failed");
                }
            }
        }

        try {
            await deleteUserData({firestore: dependencies.firestore, uid: orphanUid});
        } catch {
            dependencies.logger.error("Orphaned anonymous cleanup failed", {stage: "REMOTE_DATA"});
            throw new HttpsError("internal", "Orphaned anonymous cleanup failed");
        }

        dependencies.logger.info("Orphaned anonymous cleanup completed", {status: ORPHAN_DELETED});
        return {status: ORPHAN_DELETED};
    };
}

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
            firestore: gateways.firestore,
            logger: firebaseLogger,
        });
        return handler(request);
    },
);

function readAnonymousIdToken(data: unknown): string | null {
    if (data === null || typeof data !== "object") {
        return null;
    }
    const token = (data as OrphanCleanupPayload).anonymousIdToken;
    return typeof token === "string" && token.length > 0 ? token : null;
}

async function resolveCapturedIdentity(
    dependencies: OrphanCleanupDependencies,
    token: string,
): Promise<{skipAuthDeletion: boolean; verified: VerifiedIdentityToken}> {
    try {
        const verified = await dependencies.auth.verifyIdToken(token);
        return {skipAuthDeletion: false, verified};
    } catch (failure) {
        if (isExpiredTokenError(failure)) {
            const expiredClaims = parseExpiredAnonymousToken(token);
            if (expiredClaims !== null && typeof dependencies.auth.getUser === "function") {
                try {
                    await dependencies.auth.getUser(expiredClaims.uid);
                    throw new HttpsError("invalid-argument", "The anonymous ID token is not valid");
                } catch (userLookupError) {
                    if (isMissingAuthUser(userLookupError)) {
                        return {skipAuthDeletion: true, verified: expiredClaims};
                    }
                    if (userLookupError instanceof HttpsError) {
                        throw userLookupError;
                    }
                    dependencies.logger.error("Orphaned anonymous cleanup failed", {stage: "AUTH_USER"});
                    throw new HttpsError("internal", "Orphaned anonymous cleanup failed");
                }
            }
            throw new HttpsError("invalid-argument", "The anonymous ID token is not valid");
        }

        if (isClientTokenError(failure)) {
            throw new HttpsError("invalid-argument", "The anonymous ID token is not valid");
        }

        dependencies.logger.error("Orphaned anonymous cleanup failed", {stage: "AUTH_USER"});
        throw new HttpsError("internal", "Orphaned anonymous cleanup failed");
    }
}

function parseExpiredAnonymousToken(token: string): VerifiedIdentityToken | null {
    try {
        const parts = token.split(".");
        if (parts.length !== 3) {
            return null;
        }
        const payloadPart = parts[1];
        if (payloadPart === undefined || payloadPart.length === 0) {
            return null;
        }
        const payloadJson = Buffer.from(payloadPart, "base64url").toString("utf8");
        const payload = JSON.parse(payloadJson);
        if (payload === null || typeof payload !== "object") {
            return null;
        }
        const uid = typeof payload.uid === "string" && payload.uid.length > 0
            ? payload.uid
            : typeof payload.sub === "string" && payload.sub.length > 0
                ? payload.sub
                : null;
        if (uid === null) {
            return null;
        }
        const provider = payload.firebase?.sign_in_provider;
        if (provider !== "anonymous") {
            return null;
        }
        return {
            uid,
            firebase: {
                identities: {},
                sign_in_provider: "anonymous",
            },
        };
    } catch {
        return null;
    }
}

function isExpiredTokenError(failure: unknown): boolean {
    return failure !== null &&
        typeof failure === "object" &&
        "code" in failure &&
        failure.code === "auth/id-token-expired";
}

function isClientTokenError(failure: unknown): boolean {
    if (failure === null || typeof failure !== "object" || !("code" in failure)) {
        return false;
    }
    const code = failure.code;
    return code === "auth/argument-error" ||
        code === "auth/invalid-argument" ||
        code === "auth/invalid-id-token" ||
        code === "auth/id-token-revoked";
}

function isMissingAuthUser(failure: unknown): boolean {
    return failure !== null &&
        typeof failure === "object" &&
        "code" in failure &&
        failure.code === "auth/user-not-found";
}
