import assert from "node:assert/strict";
import test from "node:test";

import {createDeleteAccountHandler} from "../lib/callable/deleteAccount.js";
import {FirebaseAdminFirestoreDeletionGateway} from "../lib/deletion/firebaseAdminDeletionGateways.js";
import {deleteUserData} from "../lib/deletion/userDeletionService.js";

const OWNER_UID = "owner-1";

test("deleteUserData deletes fuel entries before vehicles", async () => {
  const calls = [];

  await deleteUserData({
    firestore: firestoreGateway(calls),
    uid: OWNER_UID,
  });

  assert.deepEqual(calls, [
    ["deleteCollection", OWNER_UID, "fuelEntries"],
    ["deleteCollection", OWNER_UID, "vehicles"],
  ]);
});

test("deleteUserData is idempotent when documents are already absent", async () => {
  const calls = [];
  const firestore = firestoreGateway(calls);

  await deleteUserData({firestore, uid: OWNER_UID});
  await deleteUserData({firestore, uid: OWNER_UID});

  assert.deepEqual(calls, [
    ["deleteCollection", OWNER_UID, "fuelEntries"],
    ["deleteCollection", OWNER_UID, "vehicles"],
    ["deleteCollection", OWNER_UID, "fuelEntries"],
    ["deleteCollection", OWNER_UID, "vehicles"],
  ]);
});

test("the Firebase Admin gateway scopes deletion to the target user's registered collection", async () => {
  const deletedPaths = [];
  const firestore = {
    collection(rootCollection) {
      return {
        doc(uid) {
          return {
            collection(childCollection) {
              return {path: `${rootCollection}/${uid}/${childCollection}`};
            },
          };
        },
      };
    },
    async recursiveDelete(reference) {
      deletedPaths.push(reference.path);
    },
  };
  const gateway = new FirebaseAdminFirestoreDeletionGateway(firestore);

  await gateway.deleteCollection(OWNER_UID, "fuelEntries");

  assert.deepEqual(deletedPaths, [`users/${OWNER_UID}/fuelEntries`]);
});

test("an unauthenticated request is rejected before deletion", async () => {
  const harness = deletionHarness();

  await assert.rejects(
    harness.handler({auth: undefined, data: {targetUid: OWNER_UID}}),
    (failure) => failure.code === "unauthenticated",
  );

  assert.deepEqual(harness.calls, []);
});

test("a malformed target UID is rejected before deletion", async () => {
  const harness = deletionHarness();

  await assert.rejects(
    harness.handler(authenticatedRequest({targetUid: ""})),
    (failure) => failure.code === "invalid-argument",
  );

  assert.deepEqual(harness.calls, []);
});

test("a caller cannot delete a different UID", async () => {
  const harness = deletionHarness();

  await assert.rejects(
    harness.handler(authenticatedRequest({targetUid: "another-owner"})),
    (failure) => failure.code === "permission-denied",
  );

  assert.deepEqual(harness.calls, []);
});

test("account deletion removes remote data before the Auth user", async () => {
  const harness = deletionHarness();

  const result = await harness.handler(authenticatedRequest());

  assert.deepEqual(result, {status: "ACCOUNT_DELETED"});
  assert.deepEqual(harness.calls, [
    ["deleteCollection", OWNER_UID, "fuelEntries"],
    ["deleteCollection", OWNER_UID, "vehicles"],
    ["deleteAuthUser", OWNER_UID],
  ]);
});

test("a missing Auth user is an idempotent success", async () => {
  const harness = deletionHarness({authUserMissing: true});

  const result = await harness.handler(authenticatedRequest());

  assert.deepEqual(result, {status: "ACCOUNT_DELETED"});
  assert.deepEqual(harness.calls.at(-1), ["deleteAuthUser", OWNER_UID]);
});

test("remote deletion failure is typed and prevents Auth deletion", async () => {
  const harness = deletionHarness({failCollection: "fuelEntries"});

  await assert.rejects(
    harness.handler(authenticatedRequest()),
    (failure) => failure.code === "internal" && failure.message === "Account deletion failed",
  );

  assert.deepEqual(harness.calls, [
    ["deleteCollection", OWNER_UID, "fuelEntries"],
  ]);
});

test("a retry resumes safely after partial remote deletion", async () => {
  const harness = deletionHarness({failCollectionOnce: "vehicles"});

  await assert.rejects(
    harness.handler(authenticatedRequest()),
    (failure) => failure.code === "internal",
  );
  const result = await harness.handler(authenticatedRequest());

  assert.deepEqual(result, {status: "ACCOUNT_DELETED"});
  assert.deepEqual(harness.calls, [
    ["deleteCollection", OWNER_UID, "fuelEntries"],
    ["deleteCollection", OWNER_UID, "vehicles"],
    ["deleteCollection", OWNER_UID, "fuelEntries"],
    ["deleteCollection", OWNER_UID, "vehicles"],
    ["deleteAuthUser", OWNER_UID],
  ]);
});

test("account deletion logs contain no UID, token, payload or raw failure", async () => {
  const secretToken = "secret-token-value";
  const harness = deletionHarness({failCollection: "fuelEntries"});

  await assert.rejects(
    harness.handler({
      auth: {token: {rawClaim: secretToken}, uid: OWNER_UID},
      data: {targetUid: OWNER_UID},
      rawRequest: {body: secretToken},
    }),
  );

  const serializedLogs = JSON.stringify(harness.logs);
  assert.equal(serializedLogs.includes(OWNER_UID), false);
  assert.equal(serializedLogs.includes(secretToken), false);
  assert.equal(serializedLogs.includes("database exploded"), false);
  assert.deepEqual(harness.logs, [
    ["error", "Account deletion failed", {stage: "REMOTE_DATA"}],
  ]);
});

test("Auth deletion failure logs contain no UID, token, payload or raw failure", async () => {
  const secretToken = "secret-token-value";
  const rawFailure = "Auth provider exposed a private failure";
  const harness = deletionHarness({authFailure: new Error(rawFailure)});

  await assert.rejects(
    harness.handler({
      auth: {token: {rawClaim: secretToken}, uid: OWNER_UID},
      data: {targetUid: OWNER_UID},
      rawRequest: {body: secretToken},
    }),
  );

  const serializedLogs = JSON.stringify(harness.logs);
  assert.equal(serializedLogs.includes(OWNER_UID), false);
  assert.equal(serializedLogs.includes(secretToken), false);
  assert.equal(serializedLogs.includes(rawFailure), false);
  assert.deepEqual(harness.logs, [
    ["error", "Account deletion failed", {stage: "AUTH_USER"}],
  ]);
});

test("account deletion success logs contain no UID, token or payload", async () => {
  const secretToken = "secret-token-value";
  const harness = deletionHarness();

  const result = await harness.handler({
    auth: {token: {rawClaim: secretToken}, uid: OWNER_UID},
    data: {targetUid: OWNER_UID},
    rawRequest: {body: secretToken},
  });

  assert.deepEqual(result, {status: "ACCOUNT_DELETED"});
  const serializedLogs = JSON.stringify(harness.logs);
  assert.equal(serializedLogs.includes(OWNER_UID), false);
  assert.equal(serializedLogs.includes(secretToken), false);
  assert.deepEqual(harness.logs, [
    ["info", "Account deletion completed", {status: "ACCOUNT_DELETED"}],
  ]);
});

function authenticatedRequest(data = {targetUid: OWNER_UID}) {
  return {
    auth: {token: {}, uid: OWNER_UID},
    data,
  };
}

function firestoreGateway(calls, {failCollection, failCollectionOnce} = {}) {
  let remainingOneShotFailure = failCollectionOnce;
  return {
    async deleteCollection(uid, collection) {
      calls.push(["deleteCollection", uid, collection]);
      if (collection === failCollection || collection === remainingOneShotFailure) {
        remainingOneShotFailure = undefined;
        throw new Error("database exploded");
      }
    },
  };
}

function deletionHarness({
  authFailure,
  authUserMissing = false,
  failCollection,
  failCollectionOnce,
} = {}) {
  const calls = [];
  const logs = [];
  const handler = createDeleteAccountHandler({
    auth: {
      async deleteUser(uid) {
        calls.push(["deleteAuthUser", uid]);
        if (authFailure !== undefined) {
          throw authFailure;
        }
        if (authUserMissing) {
          throw Object.assign(new Error("already gone"), {code: "auth/user-not-found"});
        }
      },
    },
    firestore: firestoreGateway(calls, {failCollection, failCollectionOnce}),
    logger: {
      error(message, context) {
        logs.push(["error", message, context]);
      },
      info(message, context) {
        logs.push(["info", message, context]);
      },
    },
  });
  return {calls, handler, logs};
}
