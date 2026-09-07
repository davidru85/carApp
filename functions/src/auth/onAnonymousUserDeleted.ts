import {logger as firebaseLogger} from "firebase-functions";
import {auth} from "firebase-functions/v1";

import {firebaseAdminDeletionGateways} from "../deletion/firebaseAdminDeletionGateways.js";
import {
    deleteUserData,
    type UserDataFirestoreGateway,
} from "../deletion/userDeletionService.js";

interface AuthTriggerLogger {
    error(message: string, context: {path: "NATIVE_TRIGGER"}): void;
    info(
        message: string,
        context: {path: "NATIVE_TRIGGER"} | {reason: "MISSING_UID" | "NOT_ANONYMOUS"},
    ): void;
}

interface AnonymousDeletionDependencies {
    firestore: UserDataFirestoreGateway;
    logger: AuthTriggerLogger;
}

interface DeletedAuthUser {
    providerData?: ReadonlyArray<{providerId?: string}>;
    uid?: string;
}

function isAnonymous(user: DeletedAuthUser): boolean {
    return (user.providerData ?? []).length === 0;
}

export function createAnonymousDeletionHandler(dependencies: AnonymousDeletionDependencies) {
    return async (user: DeletedAuthUser): Promise<void> => {
        const uid = user.uid;
        if (uid === undefined || uid.length === 0) {
            dependencies.logger.info("Anonymous cleanup skipped", {reason: "MISSING_UID"});
            return;
        }
        if (!isAnonymous(user)) {
            dependencies.logger.info("Anonymous cleanup skipped", {reason: "NOT_ANONYMOUS"});
            return;
        }

        try {
            await deleteUserData({firestore: dependencies.firestore, uid});
        } catch (failure) {
            dependencies.logger.error("Anonymous cleanup failed", {path: "NATIVE_TRIGGER"});
            throw failure;
        }
        dependencies.logger.info("Anonymous cleanup invoked", {path: "NATIVE_TRIGGER"});
    };
}

export const onAnonymousUserDeleted = auth
    .user()
    .onDelete(async (user) => {
        const gateways = firebaseAdminDeletionGateways();
        const handler = createAnonymousDeletionHandler({
            firestore: gateways.firestore,
            logger: firebaseLogger,
        });
        return handler(user);
    });
