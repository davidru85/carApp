import { readFileSync } from "node:fs";
import test from "node:test";

import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from "@firebase/rules-unit-testing";
import {
  Timestamp,
  deleteDoc,
  doc,
  getDoc,
  serverTimestamp,
  setDoc,
} from "firebase/firestore";

const PROJECT_ID = "davidruiz-carapp-dev";
const OWNER_ID = "anonymous-owner";
const OTHER_OWNER_ID = "other-owner";
const VEHICLE_ID = "123e4567-e89b-42d3-a456-426614174000";
const LOCAL_ONLY_KEYS = [
  "syncState",
  "localRevision",
  "localMutationSeq",
  "serverUpdatedAt",
  "nameFold",
  "currentOdometerKm",
];

function uuid(sequence) {
  return `123e4567-e89b-42d3-a456-${sequence.toString(16).padStart(12, "0")}`;
}

function vehicle(ownerId = OWNER_ID, id = VEHICLE_ID) {
  return {
    id,
    ownerId,
    name: "Roadster",
    initialOdometerKm: 0,
    brand: null,
    model: null,
    fuelType: "GASOLINE",
    createdAt: Timestamp.fromMillis(1_700_000_000_000),
    updatedAt: serverTimestamp(),
    deleted: false,
    deletedAt: null,
    schemaVersion: 1,
  };
}

async function withTestEnvironment(run) {
  const testEnvironment = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules: readFileSync("firestore/rules/main.rules", "utf8"),
    },
  });

  try {
    await run(testEnvironment);
  } finally {
    await testEnvironment.cleanup();
  }
}

function anonymousOwnerContext(testEnvironment, ownerId = OWNER_ID) {
  return testEnvironment.authenticatedContext(ownerId, {
    firebase: { sign_in_provider: "anonymous" },
  });
}

function vehicleReference(context, id = VEHICLE_ID, ownerId = OWNER_ID) {
  return doc(context.firestore(), `users/${ownerId}/vehicles/${id}`);
}

function withoutKey(payload, key) {
  const result = { ...payload };
  delete result[key];
  return result;
}

test("an anonymous user can create and read a valid vehicle under their own UID", async () => {
  await withTestEnvironment(async (testEnvironment) => {
    const ownerContext = anonymousOwnerContext(testEnvironment);
    const vehicleReference = doc(
      ownerContext.firestore(),
      `users/${OWNER_ID}/vehicles/${VEHICLE_ID}`,
    );

    await assertSucceeds(setDoc(vehicleReference, vehicle()));
    await assertSucceeds(getDoc(vehicleReference));
  });
});

test("a user cannot access another user's vehicle collection", async () => {
  await withTestEnvironment(async (testEnvironment) => {
    const ownerContext = anonymousOwnerContext(testEnvironment);
    const otherVehicleReference = doc(
      ownerContext.firestore(),
      `users/${OTHER_OWNER_ID}/vehicles/${VEHICLE_ID}`,
    );

    await assertFails(getDoc(otherVehicleReference));
    await assertFails(setDoc(otherVehicleReference, vehicle(OTHER_OWNER_ID)));
  });
});

test("an unauthenticated client cannot access user data", async () => {
  await withTestEnvironment(async (testEnvironment) => {
    const unauthenticatedContext = testEnvironment.unauthenticatedContext();
    const vehicleReference = doc(
      unauthenticatedContext.firestore(),
      `users/${OWNER_ID}/vehicles/${VEHICLE_ID}`,
    );

    await assertFails(getDoc(vehicleReference));
    await assertFails(setDoc(vehicleReference, vehicle()));
  });
});

test("a user cannot create a document whose ownerId differs from their UID", async () => {
  await withTestEnvironment(async (testEnvironment) => {
    const ownerContext = anonymousOwnerContext(testEnvironment);
    const vehicleReference = doc(
      ownerContext.firestore(),
      `users/${OWNER_ID}/vehicles/${VEHICLE_ID}`,
    );

    await assertFails(setDoc(vehicleReference, vehicle(OTHER_OWNER_ID)));
  });
});

test("unknown collections are denied", async () => {
  await withTestEnvironment(async (testEnvironment) => {
    const ownerContext = anonymousOwnerContext(testEnvironment);
    const unknownReference = doc(
      ownerContext.firestore(),
      `users/${OWNER_ID}/unknown/${VEHICLE_ID}`,
    );

    await assertFails(getDoc(unknownReference));
    await assertFails(setDoc(unknownReference, vehicle()));
  });
});

test("client hard deletes are denied", async () => {
  await withTestEnvironment(async (testEnvironment) => {
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
      const vehicleReference = doc(
        context.firestore(),
        `users/${OWNER_ID}/vehicles/${VEHICLE_ID}`,
      );
      await setDoc(vehicleReference, vehicle());
    });

    const ownerContext = anonymousOwnerContext(testEnvironment);
    const vehicleReference = doc(
      ownerContext.firestore(),
      `users/${OWNER_ID}/vehicles/${VEHICLE_ID}`,
    );

    await assertFails(deleteDoc(vehicleReference));
  });
});

test("vehicle writes require every closed-schema key", async (t) => {
  await withTestEnvironment(async (testEnvironment) => {
    const ownerContext = anonymousOwnerContext(testEnvironment);
    const requiredKeys = Object.keys(vehicle());

    for (const [index, key] of requiredKeys.entries()) {
      await t.test(`missing ${key}`, async () => {
        const id = uuid(100 + index);
        await assertFails(
          setDoc(
            vehicleReference(ownerContext, id),
            withoutKey(vehicle(OWNER_ID, id), key),
          ),
        );
      });
    }
  });
});

test("vehicle writes reject extra and local-only keys", async (t) => {
  await withTestEnvironment(async (testEnvironment) => {
    const ownerContext = anonymousOwnerContext(testEnvironment);
    const forbiddenKeys = ["unexpected", ...LOCAL_ONLY_KEYS];

    for (const [index, key] of forbiddenKeys.entries()) {
      await t.test(key, async () => {
        const id = uuid(200 + index);
        await assertFails(
          setDoc(vehicleReference(ownerContext, id), {
            ...vehicle(OWNER_ID, id),
            [key]: "forbidden",
          }),
        );
      });
    }
  });
});

test("vehicle writes enforce field types, ranges, enums, timestamps, and identity", async (t) => {
  await withTestEnvironment(async (testEnvironment) => {
    const ownerContext = anonymousOwnerContext(testEnvironment);
    const invalidCases = [
      ["id type", { id: 42 }],
      ["document ID match", { id: uuid(999) }],
      ["UUID v4", { id: "not-a-uuid" }],
      ["name type", { name: 42 }],
      ["name minimum", { name: "" }],
      ["name maximum", { name: "x".repeat(41) }],
      ["initial odometer type", { initialOdometerKm: 1.5 }],
      ["initial odometer minimum", { initialOdometerKm: -1 }],
      ["initial odometer maximum", { initialOdometerKm: 2_000_001 }],
      ["brand type", { brand: 42 }],
      ["brand minimum", { brand: "" }],
      ["brand maximum", { brand: "x".repeat(41) }],
      ["model type", { model: 42 }],
      ["model minimum", { model: "" }],
      ["model maximum", { model: "x".repeat(41) }],
      ["fuel type enum", { fuelType: "ELECTRIC" }],
      ["created timestamp type", { createdAt: "not-a-timestamp" }],
      [
        "server timestamp",
        { updatedAt: Timestamp.fromMillis(1_700_000_000_001) },
      ],
      ["deleted type", { deleted: 0 }],
      [
        "active deleted shape",
        { deleted: false, deletedAt: Timestamp.fromMillis(1_700_000_000_000) },
      ],
      ["tombstone deleted shape", { deleted: true, deletedAt: null }],
      ["tombstone timestamp type", { deleted: true, deletedAt: "now" }],
      ["schema version type", { schemaVersion: 1.5 }],
      ["lower schema version", { schemaVersion: 0 }],
      ["higher schema version", { schemaVersion: 2 }],
    ];

    for (const [index, [name, changes]] of invalidCases.entries()) {
      await t.test(name, async () => {
        const id = name === "UUID v4" ? "not-a-uuid" : uuid(300 + index);
        await assertFails(
          setDoc(vehicleReference(ownerContext, id), {
            ...vehicle(OWNER_ID, id),
            ...changes,
          }),
        );
      });
    }
  });
});

test("valid vehicle boundary values, enum values, tombstones, and updates are accepted", async (t) => {
  await withTestEnvironment(async (testEnvironment) => {
    const ownerContext = anonymousOwnerContext(testEnvironment);

    for (const [index, fuelType] of [
      "GASOLINE",
      "DIESEL",
      "LPG",
      "CNG",
      "OTHER",
    ].entries()) {
      await t.test(fuelType, async () => {
        const id = uuid(400 + index);
        await assertSucceeds(
          setDoc(vehicleReference(ownerContext, id), {
            ...vehicle(OWNER_ID, id),
            name: "x".repeat(40),
            initialOdometerKm: 2_000_000,
            brand: "x".repeat(40),
            model: "x".repeat(40),
            fuelType,
          }),
        );
      });
    }

    await t.test("tombstone", async () => {
      const id = uuid(410);
      await assertSucceeds(
        setDoc(vehicleReference(ownerContext, id), {
          ...vehicle(OWNER_ID, id),
          deleted: true,
          deletedAt: Timestamp.fromMillis(1_700_000_000_000),
        }),
      );
    });

    await t.test("full-document update", async () => {
      const id = uuid(411);
      await testEnvironment.withSecurityRulesDisabled(async (context) => {
        await setDoc(vehicleReference(context, id), {
          ...vehicle(OWNER_ID, id),
          updatedAt: Timestamp.fromMillis(1_700_000_000_000),
        });
      });

      await assertSucceeds(
        setDoc(vehicleReference(ownerContext, id), {
          ...vehicle(OWNER_ID, id),
          name: "Updated Roadster",
        }),
      );
    });
  });
});
