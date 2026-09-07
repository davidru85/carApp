import type {App} from "firebase-admin/app";
import {getApps, initializeApp} from "firebase-admin/app";
import type {Auth} from "firebase-admin/auth";
import {getAuth} from "firebase-admin/auth";
import type {Firestore} from "firebase-admin/firestore";
import {getFirestore, Timestamp} from "firebase-admin/firestore";

import type {
    AccountDeletionAuthGateway,
    AccountDeletionAuthorizationGateway,
} from "../callable/deleteAccount.js";
import type {
    OrphanCleanupAuthorizationGateway,
    OrphanCleanupAuthorizationRecord,
} from "../callable/deleteOrphanedAnonymousAccount.js";
import type {
    UserDataFirestoreGateway,
} from "./userDeletionService.js";
import {
    INTERNAL_SERVER_DATA_LOCATIONS,
    type UserFirestoreCollection,
} from "./dataLocationRegistry.js";

export class FirebaseAdminFirestoreDeletionGateway implements UserDataFirestoreGateway {
    public constructor(private readonly firestore: Firestore) {}

    public async deleteCollection(
        uid: string,
        collection: UserFirestoreCollection,
    ): Promise<void> {
        const collectionReference = this.firestore
            .collection("users")
            .doc(uid)
            .collection(collection);
        await this.firestore.recursiveDelete(collectionReference);
    }
}

export class FirebaseAdminAuthDeletionGateway implements AccountDeletionAuthGateway {
    public constructor(private readonly auth: Auth) {}

    public async deleteUser(uid: string): Promise<void> {
        await this.auth.deleteUser(uid);
    }

    public async getUser(uid: string): Promise<{providerData: ReadonlyArray<unknown>} | null> {
        try {
            const user = await this.auth.getUser(uid);
            return {providerData: user.providerData};
        } catch (failure) {
            if (isMissingAuthUser(failure)) {
                return null;
            }
            throw failure;
        }
    }

}

function isMissingAuthUser(failure: unknown): boolean {
    return failure !== null &&
        typeof failure === "object" &&
        "code" in failure &&
        failure.code === "auth/user-not-found";
}

const ORPHAN_CLEANUP_TICKETS_COLLECTION =
    INTERNAL_SERVER_DATA_LOCATIONS.firestoreCollections[0].collection;
const AUTHORIZATION_DELETE_BATCH_SIZE = 200;

export class FirebaseAdminOrphanCleanupAuthorizationGateway implements
    OrphanCleanupAuthorizationGateway,
    AccountDeletionAuthorizationGateway {
    public constructor(private readonly firestore: Firestore) {}

    public async issue(authorization: OrphanCleanupAuthorizationRecord): Promise<void> {
        await this.firestore
            .collection(ORPHAN_CLEANUP_TICKETS_COLLECTION)
            .doc(authorization.ticketHash)
            .create({
                anonymousUid: authorization.anonymousUid,
                expiresAt: Timestamp.fromMillis(authorization.expiresAtMs),
                status: authorization.status,
            });
    }

    public async get(ticketHash: string): Promise<OrphanCleanupAuthorizationRecord | null> {
        const snapshot = await this.firestore
            .collection(ORPHAN_CLEANUP_TICKETS_COLLECTION)
            .doc(ticketHash)
            .get();
        if (!snapshot.exists) {
            return null;
        }

        const data = snapshot.data();
        const anonymousUid = data?.anonymousUid;
        const expiresAt = data?.expiresAt;
        const status = data?.status;
        if (
            typeof anonymousUid !== "string" ||
            anonymousUid.length === 0 ||
            !(expiresAt instanceof Timestamp) ||
            (status !== "PENDING" && status !== "COMPLETED")
        ) {
            throw new Error("Malformed orphan cleanup authorization");
        }

        return {
            anonymousUid,
            expiresAtMs: expiresAt.toMillis(),
            status,
            ticketHash,
        };
    }

    public async complete(ticketHash: string): Promise<void> {
        await this.firestore
            .collection(ORPHAN_CLEANUP_TICKETS_COLLECTION)
            .doc(ticketHash)
            .update({status: "COMPLETED"});
    }

    public async purgeForUid(uid: string): Promise<void> {
        const snapshot = await this.firestore
            .collection(ORPHAN_CLEANUP_TICKETS_COLLECTION)
            .where("anonymousUid", "==", uid)
            .limit(AUTHORIZATION_DELETE_BATCH_SIZE)
            .get();
        if (snapshot.empty) {
            return;
        }

        const batch = this.firestore.batch();
        snapshot.docs.forEach((document) => batch.delete(document.ref));
        await batch.commit();

        if (snapshot.size === AUTHORIZATION_DELETE_BATCH_SIZE) {
            await this.purgeForUid(uid);
        }
    }
}

export function firebaseAdminDeletionGateways(): {
    auth: FirebaseAdminAuthDeletionGateway;
    firestore: FirebaseAdminFirestoreDeletionGateway;
    orphanCleanupAuthorizations: FirebaseAdminOrphanCleanupAuthorizationGateway;
} {
    const app = getApps()[0] ?? initializeApp();
    return gatewaysForApp(app);
}

function gatewaysForApp(app: App) {
    const firestore = getFirestore(app);
    return {
        auth: new FirebaseAdminAuthDeletionGateway(getAuth(app)),
        firestore: new FirebaseAdminFirestoreDeletionGateway(firestore),
        orphanCleanupAuthorizations: new FirebaseAdminOrphanCleanupAuthorizationGateway(firestore),
    };
}
