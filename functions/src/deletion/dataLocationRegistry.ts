export const USER_DATA_LOCATIONS = {
    firestoreCollections: [
        {
            collection: "fuelEntries",
            path: "users/{uid}/fuelEntries/{entryId}",
        },
        {
            collection: "vehicles",
            path: "users/{uid}/vehicles/{vehicleId}",
        },
    ],
    storagePrefixes: [],
} as const;

export type UserFirestoreCollection =
    (typeof USER_DATA_LOCATIONS.firestoreCollections)[number]["collection"];
