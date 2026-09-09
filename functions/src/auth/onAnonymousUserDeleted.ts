import {logger as firebaseLogger} from "firebase-functions";
import {region} from "firebase-functions/v1";

import {firebaseAdminDeletionGateways} from "../deletion/firebaseAdminDeletionGateways.js";
import {
    deleteUserData,
    type UserDataFirestoreGateway,
} from "../deletion/userDeletionService.js";
import {isAnonymousAuthUser} from "./anonymousUserEligibility.js";

const ANONYMOUS_CLEANUP_FAILED = "Anonymous cleanup failed";

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

export function createAnonymousDeletionHandler(dependencies: AnonymousDeletionDependencies) {
    return async (user: DeletedAuthUser): Promise<void> => {
        const uid = user.uid;
        if (uid === undefined || uid.length === 0) {
            dependencies.logger.info("Anonymous cleanup skipped", {reason: "MISSING_UID"});
            return;
        }
        if (!isAnonymousAuthUser(user)) {
            dependencies.logger.info("Anonymous cleanup skipped", {reason: "NOT_ANONYMOUS"});
            return;
        }

        try {
            await deleteUserData({firestore: dependencies.firestore, uid});
        } catch {
            dependencies.logger.error("Anonymous cleanup failed", {path: "NATIVE_TRIGGER"});
            // An uncaught trigger exception is reported verbatim to runtime logging and Error
            // Reporting, so the provider failure is never rethrown: its message alone can carry a
            // UID-bearing Firestore path. The rejection itself is what `failurePolicy: true`
            // retries, so it is preserved as a newly constructed error with no cause.
            throw new Error(ANONYMOUS_CLEANUP_FAILED);
        }
        dependencies.logger.info("Anonymous cleanup invoked", {path: "NATIVE_TRIGGER"});
    };
}

export const onAnonymousUserDeleted = region("europe-west1")
    .runWith({
        failurePolicy: true,
        maxInstances: 2,
        memory: "256MB",
        timeoutSeconds: 60,
    })
    .auth
    .user()
    .onDelete(async (user) => {
        const gateways = firebaseAdminDeletionGateways();
        const handler = createAnonymousDeletionHandler({
            firestore: gateways.firestore,
            logger: firebaseLogger,
        });
        return handler(user);
    });
