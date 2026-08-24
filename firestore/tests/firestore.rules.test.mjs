import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from "@firebase/rules-unit-testing";
import {
  Timestamp,
  collection,
  deleteDoc,
  doc,
  documentId,
  getDoc,
  getDocs,
  limit,
  orderBy,
  query,
  serverTimestamp,
  setDoc,
  startAfter,
  startAt,
  where,
} from "firebase/firestore";

const PROJECT_ID = "davidruiz-carapp-dev";
const OWNER_ID = "anonymous-owner";
const OTHER_OWNER_ID = "other-owner";
const VEHICLE_ID = "123e4567-e89b-42d3-a456-426614174000";
const ENTRY_ID = "123e4567-e89b-42d3-a456-426614174001";
const ORPHAN_VEHICLE_ID = "123e4567-e89b-42d3-a456-426614174002";
const LOCAL_ONLY_KEYS = [
  "syncState",
  "localRevision",
  "localMutationSeq",
  "serverUpdatedAt",
  "nameFold",
  "currentOdometerKm",
];
const SUPPORTED_CURRENCY_CODES = [
  "ARS",
  "AUD",
  "BRL",
  "CAD",
  "CHF",
  "COP",
  "CZK",
  "DKK",
  "EUR",
  "GBP",
  "HUF",
  "MAD",
  "MXN",
  "NOK",
  "NZD",
  "PEN",
  "PLN",
  "RON",
  "SEK",
  "USD",
  "UYU",
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

function fuelEntry(
  ownerId = OWNER_ID,
  id = ENTRY_ID,
  vehicleId = ORPHAN_VEHICLE_ID,
) {
  return {
    id,
    ownerId,
    vehicleId,
    date: Timestamp.fromMillis(1_700_000_000_000),
    odometerKm: 100,
    litersScaled: 50_000,
    pricePerLiterScaled: 180_000,
    totalCostMinor: 9_000,
    currency: "EUR",
    isFullTank: true,
    hasMissedEntries: false,
    odometerInconsistent: false,
    notes: null,
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

function fuelEntryReference(context, id = ENTRY_ID, ownerId = OWNER_ID) {
  return doc(context.firestore(), `users/${ownerId}/fuelEntries/${id}`);
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

test("an orphan fuel entry can be created and read under the owner's UID", async () => {
  await withTestEnvironment(async (testEnvironment) => {
    const ownerContext = anonymousOwnerContext(testEnvironment);
    const entryReference = fuelEntryReference(ownerContext);

    await assertSucceeds(setDoc(entryReference, fuelEntry()));
    await assertSucceeds(getDoc(entryReference));
  });
});

test("fuel-entry writes require every closed-schema key", async (t) => {
  await withTestEnvironment(async (testEnvironment) => {
    const ownerContext = anonymousOwnerContext(testEnvironment);
    const requiredKeys = Object.keys(fuelEntry());

    for (const [index, key] of requiredKeys.entries()) {
      await t.test(`missing ${key}`, async () => {
        const id = uuid(500 + index);
        await assertFails(
          setDoc(
            fuelEntryReference(ownerContext, id),
            withoutKey(fuelEntry(OWNER_ID, id), key),
          ),
        );
      });
    }
  });
});

test("fuel-entry writes reject extra and local-only keys", async (t) => {
  await withTestEnvironment(async (testEnvironment) => {
    const ownerContext = anonymousOwnerContext(testEnvironment);
    const forbiddenKeys = ["unexpected", ...LOCAL_ONLY_KEYS];

    for (const [index, key] of forbiddenKeys.entries()) {
      await t.test(key, async () => {
        const id = uuid(600 + index);
        await assertFails(
          setDoc(fuelEntryReference(ownerContext, id), {
            ...fuelEntry(OWNER_ID, id),
            [key]: "forbidden",
          }),
        );
      });
    }
  });
});

test("fuel-entry writes enforce field types, ranges, enums, timestamps, and identity", async (t) => {
  await withTestEnvironment(async (testEnvironment) => {
    const ownerContext = anonymousOwnerContext(testEnvironment);
    const invalidCases = [
      ["id type", { id: 42 }],
      ["document ID match", { id: uuid(999) }],
      ["UUID v4", { id: "not-a-uuid" }],
      ["owner match", { ownerId: OTHER_OWNER_ID }],
      ["vehicle ID type", { vehicleId: 42 }],
      ["vehicle UUID v4", { vehicleId: "not-a-uuid" }],
      ["date type", { date: "not-a-timestamp" }],
      ["date minimum", { date: Timestamp.fromMillis(-1) }],
      [
        "date maximum",
        { date: Timestamp.fromMillis(Date.now() + 2 * 60 * 60 * 1000) },
      ],
      ["odometer type", { odometerKm: 1.5 }],
      ["odometer minimum", { odometerKm: -1 }],
      ["odometer maximum", { odometerKm: 2_000_001 }],
      ["liters type", { litersScaled: 1.5 }],
      ["liters minimum", { litersScaled: 0 }],
      ["liters maximum", { litersScaled: 500_001 }],
      ["price type", { pricePerLiterScaled: 1.5 }],
      ["price minimum", { pricePerLiterScaled: 0 }],
      ["price maximum", { pricePerLiterScaled: 1_000_000 }],
      ["total type", { totalCostMinor: 1.5 }],
      ["total minimum", { totalCostMinor: 0 }],
      ["total maximum", { totalCostMinor: 100_000_000 }],
      ["currency type", { currency: 42 }],
      ["currency enum", { currency: "JPY" }],
      ["full-tank type", { isFullTank: 1 }],
      ["missed-entries type", { hasMissedEntries: 0 }],
      ["odometer-inconsistent type", { odometerInconsistent: 0 }],
      ["notes type", { notes: 42 }],
      ["notes minimum", { notes: "" }],
      ["notes maximum", { notes: "x".repeat(281) }],
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
        const id = name === "UUID v4" ? "not-a-uuid" : uuid(700 + index);
        await assertFails(
          setDoc(fuelEntryReference(ownerContext, id), {
            ...fuelEntry(OWNER_ID, id),
            ...changes,
          }),
        );
      });
    }
  });
});

test("valid fuel-entry boundary values, currencies, tombstones, and updates are accepted", async (t) => {
  await withTestEnvironment(async (testEnvironment) => {
    const ownerContext = anonymousOwnerContext(testEnvironment);

    for (const [index, currency] of SUPPORTED_CURRENCY_CODES.entries()) {
      await t.test(currency, async () => {
        const id = uuid(800 + index);
        await assertSucceeds(
          setDoc(fuelEntryReference(ownerContext, id), {
            ...fuelEntry(OWNER_ID, id),
            date: Timestamp.fromMillis(0),
            odometerKm: 2_000_000,
            litersScaled: 500_000,
            pricePerLiterScaled: 999_999,
            totalCostMinor: 99_999_999,
            currency,
            isFullTank: false,
            hasMissedEntries: true,
            odometerInconsistent: true,
            notes: "x".repeat(280),
          }),
        );
      });
    }

    await t.test("date within the allowed future window", async () => {
      const id = uuid(830);
      await assertSucceeds(
        setDoc(fuelEntryReference(ownerContext, id), {
          ...fuelEntry(OWNER_ID, id),
          date: Timestamp.fromMillis(Date.now() + 30 * 60 * 1000),
        }),
      );
    });

    await t.test("tombstone", async () => {
      const id = uuid(831);
      await assertSucceeds(
        setDoc(fuelEntryReference(ownerContext, id), {
          ...fuelEntry(OWNER_ID, id),
          deleted: true,
          deletedAt: Timestamp.fromMillis(1_700_000_000_000),
        }),
      );
    });

    await t.test("full-document update", async () => {
      const id = uuid(832);
      await testEnvironment.withSecurityRulesDisabled(async (context) => {
        await setDoc(fuelEntryReference(context, id), {
          ...fuelEntry(OWNER_ID, id),
          updatedAt: Timestamp.fromMillis(1_700_000_000_000),
        });
      });

      await assertSucceeds(
        setDoc(fuelEntryReference(ownerContext, id), {
          ...fuelEntry(OWNER_ID, id),
          notes: "Updated notes",
        }),
      );
    });
  });
});

test("the delta-pull query paginates deterministically and returns tombstones", async () => {
  await withTestEnvironment(async (testEnvironment) => {
    const firstTimestamp = Timestamp.fromMillis(1_700_000_000_000);
    const secondTimestamp = Timestamp.fromMillis(1_700_000_001_000);
    const firstId = uuid(900);
    const tombstoneId = uuid(901);
    const finalId = uuid(902);

    await testEnvironment.withSecurityRulesDisabled(async (context) => {
      await Promise.all([
        setDoc(vehicleReference(context, firstId), {
          ...vehicle(OWNER_ID, firstId),
          updatedAt: firstTimestamp,
        }),
        setDoc(vehicleReference(context, tombstoneId), {
          ...vehicle(OWNER_ID, tombstoneId),
          updatedAt: secondTimestamp,
          deleted: true,
          deletedAt: secondTimestamp,
        }),
        setDoc(vehicleReference(context, finalId), {
          ...vehicle(OWNER_ID, finalId),
          updatedAt: secondTimestamp,
        }),
      ]);
    });

    const ownerContext = anonymousOwnerContext(testEnvironment);
    const vehicles = collection(
      ownerContext.firestore(),
      `users/${OWNER_ID}/vehicles`,
    );
    const firstPage = await assertSucceeds(
      getDocs(
        query(
          vehicles,
          where("updatedAt", ">=", firstTimestamp),
          orderBy("updatedAt", "asc"),
          orderBy(documentId(), "asc"),
          startAt(firstTimestamp, ""),
          limit(2),
        ),
      ),
    );

    assert.deepEqual(
      firstPage.docs.map((snapshot) => snapshot.id),
      [firstId, tombstoneId],
    );
    assert.equal(firstPage.docs[1].data().deleted, true);

    const secondPage = await assertSucceeds(
      getDocs(
        query(
          vehicles,
          where("updatedAt", ">=", firstTimestamp),
          orderBy("updatedAt", "asc"),
          orderBy(documentId(), "asc"),
          startAfter(secondTimestamp, tombstoneId),
          limit(2),
        ),
      ),
    );

    assert.deepEqual(
      secondPage.docs.map((snapshot) => snapshot.id),
      [finalId],
    );
  });
});
