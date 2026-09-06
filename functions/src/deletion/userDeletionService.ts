import {
    USER_DATA_LOCATIONS,
    type UserFirestoreCollection,
} from "./dataLocationRegistry.js";

export interface UserDataFirestoreGateway {
    deleteCollection(uid: string, collection: UserFirestoreCollection): Promise<void>;
}

export interface DeleteUserDataInput {
    firestore: UserDataFirestoreGateway;
    uid: string;
}

export async function deleteUserData(input: DeleteUserDataInput): Promise<void> {
    for (const location of USER_DATA_LOCATIONS.firestoreCollections) {
        await input.firestore.deleteCollection(input.uid, location.collection);
    }
}
