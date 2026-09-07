import assert from "node:assert/strict";
import test from "node:test";

import {getApps, initializeApp} from "firebase-admin/app";
import {getFirestore, Timestamp} from "firebase-admin/firestore";

import {createOrphanCleanupHandler} from "../lib/callable/deleteOrphanedAnonymousAccount.js";
import {FirebaseAdminFirestoreDeletionGateway} from "../lib/deletion/firebaseAdminDeletionGateways.js";

const PROJECT_ID = "davidruiz-carapp-dev";
const ORPHAN_UID = "emulator-orphan-uid";
const OTHER_UID = "emulator-other-uid";
const PERMANENT_CALLER_UID = "emulator-permanent-caller-uid";
const ORPHAN_TOKEN = "emulator-orphan-token";

const isEmulatorRunning = Boolean(process.env.FIRESTORE_EMULATOR_HOST);

test(
  "orphaned anonymous account cleanup Admin path deletes orphan data and preserves other users in Firestore emulator",
  {skip: !isEmulatorRunning ? "FIRESTORE_EMULATOR_HOST is not set" : false},
  async () => {
    const app = getApps().length > 0 ? getApps()[0] : initializeApp({projectId: PROJECT_ID});
    const db = getFirestore(app);

    // 1. Seed data under users/{orphanUid}/fuelEntries and users/{orphanUid}/vehicles
    const orphanVehicleRef = db.collection("users").doc(ORPHAN_UID).collection("vehicles").doc("v-orphan-1");
    const orphanEntryRef = db.collection("users").doc(ORPHAN_UID).collection("fuelEntries").doc("e-orphan-1");
    await orphanVehicleRef.set({name: "Orphan Car", createdAt: Timestamp.now()});
    await orphanEntryRef.set({liters: 45, createdAt: Timestamp.now()});

    // 2. Seed data under users/{otherUid}/fuelEntries and users/{otherUid}/vehicles
    const otherVehicleRef = db.collection("users").doc(OTHER_UID).collection("vehicles").doc("v-other-1");
    const otherEntryRef = db.collection("users").doc(OTHER_UID).collection("fuelEntries").doc("e-other-1");
    await otherVehicleRef.set({name: "Other Car", createdAt: Timestamp.now()});
    await otherEntryRef.set({liters: 50, createdAt: Timestamp.now()});

    // Verify seeded documents exist
    const preOrphanVehicles = await db.collection("users").doc(ORPHAN_UID).collection("vehicles").get();
    const preOtherVehicles = await db.collection("users").doc(OTHER_UID).collection("vehicles").get();
    assert.equal(preOrphanVehicles.size, 1);
    assert.equal(preOtherVehicles.size, 1);

    // 3. Execute the callable handler using the real FirebaseAdminFirestoreDeletionGateway
    const firestoreGateway = new FirebaseAdminFirestoreDeletionGateway(db);
    const deletedAuthUsers = [];
    const logs = [];

    const handler = createOrphanCleanupHandler({
      auth: {
        async deleteUser(uid) {
          deletedAuthUsers.push(uid);
        },
        async getUser(uid) {
          return {uid};
        },
        async verifyIdToken(token) {
          assert.equal(token, ORPHAN_TOKEN);
          return {
            uid: ORPHAN_UID,
            firebase: {
              identities: {},
              sign_in_provider: "anonymous",
            },
          };
        },
      },
      firestore: firestoreGateway,
      logger: {
        error(message, context) {
          logs.push(["error", message, context]);
        },
        info(message, context) {
          logs.push(["info", message, context]);
        },
      },
      publicKeyFetcher: {
        async fetchKeys() {
          return {};
        },
      },
    });

    // Trigger delivery is suppressed: no Auth trigger runs or is required for this cleanup path
    const result = await handler({
      auth: {
        token: {
          firebase: {
            sign_in_provider: "google.com",
          },
        },
        uid: PERMANENT_CALLER_UID,
      },
      data: {anonymousIdToken: ORPHAN_TOKEN},
    });

    assert.deepEqual(result, {status: "ORPHANED_ANONYMOUS_ACCOUNT_DELETED"});
    assert.deepEqual(deletedAuthUsers, [ORPHAN_UID]);

    // 4. Prove registered collections under users/{orphanUid} are completely deleted
    const postOrphanVehicles = await db.collection("users").doc(ORPHAN_UID).collection("vehicles").get();
    const postOrphanEntries = await db.collection("users").doc(ORPHAN_UID).collection("fuelEntries").get();
    assert.equal(postOrphanVehicles.size, 0);
    assert.equal(postOrphanEntries.size, 0);

    // 5. Prove collections under users/{otherUid} remain completely untouched
    const postOtherVehicles = await db.collection("users").doc(OTHER_UID).collection("vehicles").get();
    const postOtherEntries = await db.collection("users").doc(OTHER_UID).collection("fuelEntries").get();
    assert.equal(postOtherVehicles.size, 1);
    assert.equal(postOtherEntries.size, 1);
    assert.equal(postOtherVehicles.docs[0].data().name, "Other Car");
    assert.equal(postOtherEntries.docs[0].data().liters, 50);

    // Clean up remaining test data
    await otherVehicleRef.delete();
    await otherEntryRef.delete();
  },
);
