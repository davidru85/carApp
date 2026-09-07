import type {App} from "firebase-admin/app";
import {getApps, initializeApp} from "firebase-admin/app";
import type {Auth} from "firebase-admin/auth";
import {getAuth} from "firebase-admin/auth";
import type {Firestore} from "firebase-admin/firestore";
import {getFirestore} from "firebase-admin/firestore";

import type {AccountDeletionAuthGateway} from "../callable/deleteAccount.js";
import type {
    UserDataFirestoreGateway,
} from "./userDeletionService.js";
import type {UserFirestoreCollection} from "./dataLocationRegistry.js";

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
}

export function firebaseAdminDeletionGateways(): {
    auth: FirebaseAdminAuthDeletionGateway;
    firestore: FirebaseAdminFirestoreDeletionGateway;
} {
    const app = getApps()[0] ?? initializeApp();
    return gatewaysForApp(app);
}

function gatewaysForApp(app: App) {
    return {
        auth: new FirebaseAdminAuthDeletionGateway(getAuth(app)),
        firestore: new FirebaseAdminFirestoreDeletionGateway(getFirestore(app)),
    };
}
