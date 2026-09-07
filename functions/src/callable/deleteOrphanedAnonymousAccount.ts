import {logger as firebaseLogger} from "firebase-functions";
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
    auth?: {uid: string};
    data: unknown;
}

export interface OrphanCleanupAuthGateway {
    deleteUser(uid: string): Promise<void>;
    verifyIdToken(token: string): Promise<VerifiedIdentityToken>;
}

export interface VerifiedIdentityToken {
    sign_in_provider?: string;
    uid?: string;
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

        const anonymousIdToken = readAnonymousIdToken(request.data);
        if (anonymousIdToken === null) {
            throw new HttpsError("invalid-argument", "A valid anonymous ID token is required");
        }

        const verified = await verifyCapturedIdentity(dependencies.auth, anonymousIdToken);
        if (verified.uid === undefined || verified.sign_in_provider !== "anonymous") {
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

        try {
            await dependencies.auth.deleteUser(orphanUid);
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

async function verifyCapturedIdentity(
    auth: OrphanCleanupAuthGateway,
    token: string,
): Promise<VerifiedIdentityToken> {
    try {
        return await auth.verifyIdToken(token);
    } catch {
        throw new HttpsError("invalid-argument", "The anonymous ID token is not valid");
    }
}

function isMissingAuthUser(failure: unknown): boolean {
    return failure !== null &&
        typeof failure === "object" &&
        "code" in failure &&
        failure.code === "auth/user-not-found";
}
