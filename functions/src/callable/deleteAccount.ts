import {logger as firebaseLogger} from "firebase-functions";
import {HttpsError, onCall} from "firebase-functions/v2/https";

import {firebaseAdminDeletionGateways} from "../deletion/firebaseAdminDeletionGateways.js";
import {
    deleteUserData,
    type UserDataFirestoreGateway,
} from "../deletion/userDeletionService.js";

const ACCOUNT_DELETED = "ACCOUNT_DELETED";

interface DeleteAccountPayload {
    targetUid?: unknown;
}

interface DeleteAccountRequest {
    auth?: {uid: string};
    data: unknown;
}

export interface AccountDeletionAuthGateway {
    deleteUser(uid: string): Promise<void>;
}

export interface AccountDeletionAuthorizationGateway {
    purgeForUid(uid: string): Promise<void>;
}

interface AccountDeletionLogger {
    error(message: string, context: {stage: "AUTHORIZATION" | "AUTH_USER" | "REMOTE_DATA"}): void;
    info(message: string, context: {status: typeof ACCOUNT_DELETED}): void;
}

interface DeleteAccountDependencies {
    auth: AccountDeletionAuthGateway;
    firestore: UserDataFirestoreGateway;
    orphanCleanupAuthorizations: AccountDeletionAuthorizationGateway;
    logger: AccountDeletionLogger;
}

export function createDeleteAccountHandler(dependencies: DeleteAccountDependencies) {
    return async (request: DeleteAccountRequest): Promise<{status: typeof ACCOUNT_DELETED}> => {
        const callerUid = request.auth?.uid;
        if (callerUid === undefined) {
            throw new HttpsError("unauthenticated", "Authentication is required");
        }

        const targetUid = readTargetUid(request.data);
        if (targetUid === null) {
            throw new HttpsError("invalid-argument", "A valid target UID is required");
        }
        if (callerUid !== targetUid) {
            throw new HttpsError("permission-denied", "The caller cannot delete this account");
        }

        try {
            await deleteUserData({firestore: dependencies.firestore, uid: targetUid});
        } catch {
            dependencies.logger.error("Account deletion failed", {stage: "REMOTE_DATA"});
            throw new HttpsError("internal", "Account deletion failed");
        }

        try {
            await dependencies.orphanCleanupAuthorizations.purgeForUid(targetUid);
        } catch {
            dependencies.logger.error("Account deletion failed", {stage: "AUTHORIZATION"});
            throw new HttpsError("internal", "Account deletion failed");
        }

        try {
            await dependencies.auth.deleteUser(targetUid);
        } catch (failure) {
            if (!isMissingAuthUser(failure)) {
                dependencies.logger.error("Account deletion failed", {stage: "AUTH_USER"});
                throw new HttpsError("internal", "Account deletion failed");
            }
        }

        dependencies.logger.info("Account deletion completed", {status: ACCOUNT_DELETED});
        return {status: ACCOUNT_DELETED};
    };
}

export const deleteAccount = onCall<DeleteAccountPayload>(
    {
        concurrency: 1,
        maxInstances: 3,
        memory: "256MiB",
        region: "europe-west1",
        timeoutSeconds: 300,
    },
    async (request) => {
        const gateways = firebaseAdminDeletionGateways();
        const handler = createDeleteAccountHandler({
            ...gateways,
            logger: firebaseLogger,
        });
        return handler(request);
    },
);

function readTargetUid(data: unknown): string | null {
    if (data === null || typeof data !== "object") {
        return null;
    }
    const targetUid = (data as DeleteAccountPayload).targetUid;
    return typeof targetUid === "string" && targetUid.length > 0 ? targetUid : null;
}

function isMissingAuthUser(failure: unknown): boolean {
    return failure !== null &&
        typeof failure === "object" &&
        "code" in failure &&
        failure.code === "auth/user-not-found";
}
