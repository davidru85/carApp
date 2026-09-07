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

export const INTERNAL_SERVER_DATA_LOCATIONS = {
    firestoreCollections: [
        {
            collection: "orphanCleanupTickets",
            path: "orphanCleanupTickets/{ticketHash}",
        },
    ],
} as const;

export type UserFirestoreCollection =
    (typeof USER_DATA_LOCATIONS.firestoreCollections)[number]["collection"];
