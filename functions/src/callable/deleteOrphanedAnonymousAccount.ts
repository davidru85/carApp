import {createHash, createPublicKey, verify as cryptoVerify} from "node:crypto";
import {logger as firebaseLogger} from "firebase-functions";
import type {DecodedIdToken} from "firebase-admin/auth";
import {HttpsError, onCall} from "firebase-functions/v2/https";

import {firebaseAdminDeletionGateways} from "../deletion/firebaseAdminDeletionGateways.js";
import {
    deleteUserData,
    type UserDataFirestoreGateway,
} from "../deletion/userDeletionService.js";

const ORPHAN_DELETED = "ORPHANED_ANONYMOUS_ACCOUNT_DELETED";
const ORPHAN_CLEANUP_TICKET_ISSUED = "ORPHAN_CLEANUP_TICKET_ISSUED";
const ORPHAN_CLEANUP_TICKET_LIFETIME_MS = 30 * 24 * 60 * 60 * 1000;
const MAX_EXPIRED_TOKEN_AGE_SECONDS = 30 * 24 * 3600;
const CLOCK_SKEW_SECONDS = 300;

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

export interface OrphanCleanupAuthorizationRecord {
    anonymousUid: string;
    expiresAtMs: number;
    status: "PENDING" | "COMPLETED";
    ticketHash: string;
}

export interface OrphanCleanupAuthorizationGateway {
    issue(authorization: OrphanCleanupAuthorizationRecord): Promise<void>;
}

interface OrphanCleanupTicketLogger {
    error(message: string, context: {stage: "AUTHORIZATION"}): void;
    info(message: string, context: {status: typeof ORPHAN_CLEANUP_TICKET_ISSUED}): void;
}

export interface OrphanCleanupTicketDependencies {
    authorizations: OrphanCleanupAuthorizationGateway;
    clock: {nowMs(): number};
    logger: OrphanCleanupTicketLogger;
    ticketGenerator(): string;
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

        const cleanupTicket = dependencies.ticketGenerator();
        const ticketHash = createHash("sha256").update(cleanupTicket, "utf8").digest("hex");
        await dependencies.authorizations.issue({
            anonymousUid,
            expiresAtMs: dependencies.clock.nowMs() + ORPHAN_CLEANUP_TICKET_LIFETIME_MS,
            status: "PENDING",
            ticketHash,
        });

        dependencies.logger.info("Orphan cleanup ticket issued", {
            status: ORPHAN_CLEANUP_TICKET_ISSUED,
        });
        return {cleanupTicket};
    };
}

export type VerifiedIdentityToken = Pick<DecodedIdToken, "uid" | "firebase">;

export interface PublicKeyFetcher {
    fetchKeys(): Promise<Record<string, string>>;
}

export class GooglePublicKeyFetcher implements PublicKeyFetcher {
    private cached: {expiresAt: number; keys: Record<string, string>} | null = null;

    public constructor(
        private readonly certsUrl: string = "https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com",
    ) {}

    public async fetchKeys(): Promise<Record<string, string>> {
        const now = Date.now();
        if (this.cached !== null && now < this.cached.expiresAt) {
            return this.cached.keys;
        }

        const response = await fetch(this.certsUrl);
        if (!response.ok) {
            throw new Error(`Failed to fetch Google public certificates: HTTP ${response.status}`);
        }

        const keys = (await response.json()) as Record<string, string>;
        let maxAgeSeconds = 21600;
        const cacheControl = response.headers.get("cache-control");
        if (cacheControl !== null) {
            const match = /max-age=(\d+)/i.exec(cacheControl);
            if (match?.[1] !== undefined) {
                maxAgeSeconds = parseInt(match[1], 10);
            }
        }

        this.cached = {
            expiresAt: now + maxAgeSeconds * 1000,
            keys,
        };
        return keys;
    }
}

export interface OrphanCleanupAuthGateway {
    deleteUser(uid: string): Promise<void>;
    getUser(uid: string): Promise<{uid: string}>;
    verifyIdToken(token: string): Promise<VerifiedIdentityToken>;
}

interface OrphanCleanupLogger {
    error(message: string, context: {stage: "AUTH_USER" | "REMOTE_DATA"}): void;
    info(message: string, context: {status: typeof ORPHAN_DELETED}): void;
}

export interface OrphanCleanupDependencies {
    auth: OrphanCleanupAuthGateway;
    firestore: UserDataFirestoreGateway;
    logger: OrphanCleanupLogger;
    publicKeyFetcher: PublicKeyFetcher;
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

const googlePublicKeyFetcher = new GooglePublicKeyFetcher();

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
            publicKeyFetcher: googlePublicKeyFetcher,
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
            const verified = await verifyExpiredAnonymousToken(
                token,
                dependencies.publicKeyFetcher,
                dependencies.logger,
            );

            try {
                await dependencies.auth.getUser(verified.uid);
                return {skipAuthDeletion: false, verified};
            } catch (userLookupError) {
                if (isMissingAuthUser(userLookupError)) {
                    return {skipAuthDeletion: true, verified};
                }
                if (userLookupError instanceof HttpsError) {
                    throw userLookupError;
                }
                dependencies.logger.error("Orphaned anonymous cleanup failed", {stage: "AUTH_USER"});
                throw new HttpsError("internal", "Orphaned anonymous cleanup failed");
            }
        }

        if (isClientTokenError(failure)) {
            throw new HttpsError("invalid-argument", "The anonymous ID token is not valid");
        }

        dependencies.logger.error("Orphaned anonymous cleanup failed", {stage: "AUTH_USER"});
        throw new HttpsError("internal", "Orphaned anonymous cleanup failed");
    }
}

interface DecodedTokenParts {
    header: {alg?: unknown; kid?: unknown};
    payload: {
        firebase?: {sign_in_provider?: unknown};
        iat?: unknown;
        sub?: unknown;
        uid?: unknown;
    };
    signedData: Buffer;
    signature: Buffer;
}

function parseTokenParts(token: string): DecodedTokenParts | null {
    const parts = token.split(".");
    if (parts.length !== 3) {
        return null;
    }
    const [headerB64, payloadB64, signatureB64] = parts;
    if (!headerB64 || !payloadB64 || !signatureB64) {
        return null;
    }
    try {
        const headerJson = Buffer.from(headerB64, "base64url").toString("utf8");
        const payloadJson = Buffer.from(payloadB64, "base64url").toString("utf8");
        const header = JSON.parse(headerJson);
        const payload = JSON.parse(payloadJson);
        if (typeof header !== "object" || header === null || typeof payload !== "object" || payload === null) {
            return null;
        }
        return {
            header,
            payload,
            signedData: Buffer.from(`${headerB64}.${payloadB64}`, "utf8"),
            signature: Buffer.from(signatureB64, "base64url"),
        };
    } catch {
        return null;
    }
}

async function verifyExpiredAnonymousToken(
    token: string,
    publicKeyFetcher: PublicKeyFetcher,
    logger: OrphanCleanupLogger,
): Promise<VerifiedIdentityToken> {
    const parsed = parseTokenParts(token);
    if (parsed === null) {
        throw new HttpsError("invalid-argument", "The anonymous ID token is not valid");
    }

    const {header, payload, signedData, signature} = parsed;

    if (header.alg !== "RS256" || typeof header.kid !== "string" || header.kid.length === 0) {
        throw new HttpsError("invalid-argument", "The anonymous ID token is not valid");
    }

    let keys: Record<string, string>;
    try {
        keys = await publicKeyFetcher.fetchKeys();
    } catch {
        logger.error("Orphaned anonymous cleanup failed", {stage: "AUTH_USER"});
        throw new HttpsError("internal", "Orphaned anonymous cleanup failed");
    }

    const pemCert = keys[header.kid];
    if (typeof pemCert !== "string" || pemCert.length === 0) {
        throw new HttpsError("invalid-argument", "The anonymous ID token is not valid");
    }

    let isValid = false;
    try {
        const publicKey = createPublicKey(pemCert);
        isValid = cryptoVerify("RSA-SHA256", signedData, publicKey, signature);
    } catch {
        isValid = false;
    }

    if (!isValid) {
        throw new HttpsError("invalid-argument", "The anonymous ID token is not valid");
    }

    const uid = typeof payload.uid === "string" && payload.uid.length > 0
        ? payload.uid
        : typeof payload.sub === "string" && payload.sub.length > 0
            ? payload.sub
            : null;
    if (uid === null) {
        throw new HttpsError("invalid-argument", "The anonymous ID token is not valid");
    }

    const provider = payload.firebase?.sign_in_provider;
    if (provider !== "anonymous") {
        throw new HttpsError("invalid-argument", "The anonymous ID token is not valid");
    }

    const nowSeconds = Math.floor(Date.now() / 1000);
    const iat = typeof payload.iat === "number" ? payload.iat : null;
    if (iat === null || iat > nowSeconds + CLOCK_SKEW_SECONDS || nowSeconds - iat > MAX_EXPIRED_TOKEN_AGE_SECONDS) {
        throw new HttpsError("invalid-argument", "The anonymous ID token is not valid");
    }

    return {
        uid,
        firebase: {
            identities: {},
            sign_in_provider: "anonymous",
        },
    };
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
