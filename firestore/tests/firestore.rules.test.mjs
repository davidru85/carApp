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

function vehicle(ownerId = OWNER_ID) {
  return {
    id: VEHICLE_ID,
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
