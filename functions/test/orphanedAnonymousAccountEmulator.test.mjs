import assert from "node:assert/strict";
import crypto from "node:crypto";
import test from "node:test";

import {getApps, initializeApp} from "firebase-admin/app";
import {getFirestore, Timestamp} from "firebase-admin/firestore";

import {
  createOrphanCleanupHandler,
  createOrphanCleanupTicketHandler,
} from "../lib/callable/deleteOrphanedAnonymousAccount.js";
import {
  FirebaseAdminFirestoreDeletionGateway,
  FirebaseAdminOrphanCleanupAuthorizationGateway,
} from "../lib/deletion/firebaseAdminDeletionGateways.js";

const PROJECT_ID = "davidruiz-carapp-dev";
const ORPHAN_UID = "emulator-orphan-uid";
const OTHER_UID = "emulator-other-uid";
const PERMANENT_CALLER_UID = "emulator-permanent-caller-uid";
const CLEANUP_TICKET = "E".repeat(43);

const isEmulatorRunning = Boolean(process.env.FIRESTORE_EMULATOR_HOST);

test(
  "server-issued ticket cleanup deletes orphan data and preserves other users in the Firestore emulator",
  {skip: !isEmulatorRunning ? "FIRESTORE_EMULATOR_HOST is not set" : false},
  async () => {
    const app = getApps().length > 0 ? getApps()[0] : initializeApp({projectId: PROJECT_ID});
    const db = getFirestore(app);
    const firestoreGateway = new FirebaseAdminFirestoreDeletionGateway(db);
    const authorizationGateway = new FirebaseAdminOrphanCleanupAuthorizationGateway(db);
    const ticketHash = crypto.createHash("sha256").update(CLEANUP_TICKET, "utf8").digest("hex");
    const ticketReference = db.collection("orphanCleanupTickets").doc(ticketHash);
    const orphanVehicleReference = db
      .collection("users").doc(ORPHAN_UID).collection("vehicles").doc("v-orphan-1");
    const orphanEntryReference = db
      .collection("users").doc(ORPHAN_UID).collection("fuelEntries").doc("e-orphan-1");
    const otherVehicleReference = db
      .collection("users").doc(OTHER_UID).collection("vehicles").doc("v-other-1");
    const otherEntryReference = db
      .collection("users").doc(OTHER_UID).collection("fuelEntries").doc("e-other-1");

    await ticketReference.delete();
    await orphanVehicleReference.set({name: "Orphan Car", createdAt: Timestamp.now()});
    await orphanEntryReference.set({liters: 45, createdAt: Timestamp.now()});
    await otherVehicleReference.set({name: "Other Car", createdAt: Timestamp.now()});
    await otherEntryReference.set({liters: 50, createdAt: Timestamp.now()});

    const logs = [];
    const issueTicket = createOrphanCleanupTicketHandler({
      authorizations: authorizationGateway,
      clock: {nowMs: () => Date.now()},
      logger: testLogger(logs),
      ticketGenerator: () => CLEANUP_TICKET,
    });
    const issued = await issueTicket({
      auth: {
        token: {firebase: {sign_in_provider: "anonymous"}},
        uid: ORPHAN_UID,
      },
      data: {},
    });
    assert.deepEqual(issued, {cleanupTicket: CLEANUP_TICKET});
    const pendingTicket = await ticketReference.get();
    assert.equal(pendingTicket.data().anonymousUid, ORPHAN_UID);
    assert.equal(pendingTicket.data().status, "PENDING");
    assert.equal(JSON.stringify(pendingTicket.data()).includes(CLEANUP_TICKET), false);

    const deletedAuthUsers = [];
    const cleanup = createOrphanCleanupHandler({
      auth: {
        async deleteUser(uid) {
          deletedAuthUsers.push(uid);
        },
        async getUser(uid) {
          return deletedAuthUsers.includes(uid) ? null : {providerData: []};
        },
      },
      authorizations: authorizationGateway,
      clock: {nowMs: () => Date.now()},
      firestore: firestoreGateway,
      logger: testLogger(logs),
    });

    // Trigger delivery is suppressed: the callable directly completes every required deletion.
    const result = await cleanup({
      auth: {
        token: {firebase: {sign_in_provider: "google.com"}},
        uid: PERMANENT_CALLER_UID,
      },
      data: {cleanupTicket: CLEANUP_TICKET},
    });

    assert.deepEqual(result, {status: "ORPHANED_ANONYMOUS_ACCOUNT_DELETED"});
    assert.deepEqual(deletedAuthUsers, [ORPHAN_UID]);
    assert.equal((await orphanVehicleReference.get()).exists, false);
    assert.equal((await orphanEntryReference.get()).exists, false);
    assert.equal((await otherVehicleReference.get()).data().name, "Other Car");
    assert.equal((await otherEntryReference.get()).data().liters, 50);
    assert.equal((await ticketReference.get()).data().status, "COMPLETED");

    const serializedLogs = JSON.stringify(logs);
    assert.equal(serializedLogs.includes(ORPHAN_UID), false);
    assert.equal(serializedLogs.includes(PERMANENT_CALLER_UID), false);
    assert.equal(serializedLogs.includes(CLEANUP_TICKET), false);

    await ticketReference.delete();
    await otherVehicleReference.delete();
    await otherEntryReference.delete();
  },
);

test(
  "the real authorization gateway purges only the records bound to the deleted UID in the Firestore emulator",
  {skip: !isEmulatorRunning ? "FIRESTORE_EMULATOR_HOST is not set" : false},
  async () => {
    const app = getApps().length > 0 ? getApps()[0] : initializeApp({projectId: PROJECT_ID});
    const db = getFirestore(app);
    const authorizationGateway = new FirebaseAdminOrphanCleanupAuthorizationGateway(db);
    const purgedUid = "emulator-purge-target-uid";
    const survivingUid = "emulator-purge-survivor-uid";
    const purgedTickets = ["purge-ticket-a", "purge-ticket-b", "purge-ticket-c"];
    const survivingTicket = "purge-ticket-survivor";
    const ticketCollection = db.collection("orphanCleanupTickets");

    await Promise.all(
      [...purgedTickets, survivingTicket].map((ticket) =>
        ticketCollection.doc(ticket).delete(),
      ),
    );
    await Promise.all(
      purgedTickets.map((ticket) =>
        ticketCollection.doc(ticket).set({
          anonymousUid: purgedUid,
          expiresAt: Timestamp.now(),
          status: "PENDING",
        }),
      ),
    );
    await ticketCollection.doc(survivingTicket).set({
      anonymousUid: survivingUid,
      expiresAt: Timestamp.now(),
      status: "PENDING",
    });

    await authorizationGateway.purgeForUid(purgedUid);

    for (const ticket of purgedTickets) {
      assert.equal((await ticketCollection.doc(ticket).get()).exists, false);
    }
    const survivor = await ticketCollection.doc(survivingTicket).get();
    assert.equal(survivor.exists, true);
    assert.equal(survivor.data().anonymousUid, survivingUid);

    await authorizationGateway.purgeForUid(purgedUid);
    for (const ticket of purgedTickets) {
      assert.equal((await ticketCollection.doc(ticket).get()).exists, false);
    }

    await ticketCollection.doc(survivingTicket).delete();
  },
);

function testLogger(logs) {
  return {
    error(message, context) {
      logs.push(["error", message, context]);
    },
    info(message, context) {
      logs.push(["info", message, context]);
    },
  };
}
