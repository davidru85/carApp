import assert from "node:assert/strict";
import test from "node:test";

import {createOrphanCleanupHandler} from "../lib/callable/deleteOrphanedAnonymousAccount.js";

const PERMANENT_UID = "permanent-1";
const ORPHAN_UID = "orphan-1";
const ORPHAN_TOKEN = "orphan-id-token";

test("an unauthenticated call is rejected before any verification or deletion", async () => {
  const harness = orphanHarness();

  await assert.rejects(
    harness.handler({auth: undefined, data: {anonymousIdToken: ORPHAN_TOKEN}}),
    (failure) => failure.code === "unauthenticated",
  );

  assert.deepEqual(harness.calls, []);
});

test("a malformed payload is rejected before token verification", async () => {
  const harness = orphanHarness();

  await assert.rejects(
    harness.handler(authed({anonymousIdToken: ""})),
    (failure) => failure.code === "invalid-argument",
  );

  assert.deepEqual(harness.calls, []);
});

test("a malformed token is rejected before any deletion", async () => {
  const harness = orphanHarness({verifyFailure: true});

  await assert.rejects(
    harness.handler(authed()),
    (failure) => failure.code === "invalid-argument",
  );

  assert.deepEqual(harness.calls, [["verifyIdToken", ORPHAN_TOKEN]]);
});

test("a permanent captured identity is rejected as not anonymous", async () => {
  const harness = orphanHarness({
    verified: verifiedToken({signInProvider: "google.com"}),
  });

  await assert.rejects(
    harness.handler(authed()),
    (failure) => failure.code === "failed-precondition",
  );

  assert.deepEqual(harness.calls, [["verifyIdToken", ORPHAN_TOKEN]]);
});

test("deleting the current permanent UID is rejected before Admin deletion", async () => {
  const harness = orphanHarness({verified: verifiedToken({uid: PERMANENT_UID})});

  await assert.rejects(
    harness.handler(authed()),
    (failure) => failure.code === "failed-precondition",
  );

  assert.deepEqual(harness.calls, [["verifyIdToken", ORPHAN_TOKEN]]);
});

test("the collision path deletes the orphaned Auth account, then remote data, without the trigger", async () => {
  const harness = orphanHarness();

  const result = await harness.handler(authed());

  assert.deepEqual(result, {status: "ORPHANED_ANONYMOUS_ACCOUNT_DELETED"});
  assert.deepEqual(harness.calls, [
    ["verifyIdToken", ORPHAN_TOKEN],
    ["deleteAuthUser", ORPHAN_UID],
    ["deleteCollection", ORPHAN_UID, "fuelEntries"],
    ["deleteCollection", ORPHAN_UID, "vehicles"],
  ]);
});

test("a retry after Auth deletion but before data deletion resumes with remote deletion", async () => {
  const harness = orphanHarness({authUserMissing: true});

  const result = await harness.handler(authed());

  assert.deepEqual(result, {status: "ORPHANED_ANONYMOUS_ACCOUNT_DELETED"});
  assert.deepEqual(harness.calls, [
    ["verifyIdToken", ORPHAN_TOKEN],
    ["deleteAuthUser", ORPHAN_UID],
    ["deleteCollection", ORPHAN_UID, "fuelEntries"],
    ["deleteCollection", ORPHAN_UID, "vehicles"],
  ]);
});

test("a failed attempt followed by a full retry converges", async () => {
  const harness = orphanHarness({failCollectionOnce: "vehicles"});

  await assert.rejects(
    harness.handler(authed()),
    (failure) => failure.code === "internal",
  );
  const result = await harness.handler(authed());

  assert.deepEqual(result, {status: "ORPHANED_ANONYMOUS_ACCOUNT_DELETED"});
  assert.deepEqual(harness.calls, [
    ["verifyIdToken", ORPHAN_TOKEN],
    ["deleteAuthUser", ORPHAN_UID],
    ["deleteCollection", ORPHAN_UID, "fuelEntries"],
    ["deleteCollection", ORPHAN_UID, "vehicles"],
    ["verifyIdToken", ORPHAN_TOKEN],
    ["deleteAuthUser", ORPHAN_UID],
    ["deleteCollection", ORPHAN_UID, "fuelEntries"],
    ["deleteCollection", ORPHAN_UID, "vehicles"],
  ]);
});

test("a remote-data failure is typed and logged with the stage only", async () => {
  const harness = orphanHarness({failCollection: "fuelEntries"});

  await assert.rejects(
    harness.handler(authed()),
    (failure) => failure.code === "internal" &&
      failure.message === "Orphaned anonymous cleanup failed",
  );

  assert.deepEqual(harness.logs, [
    ["error", "Orphaned anonymous cleanup failed", {stage: "REMOTE_DATA"}],
  ]);
});

test("an Admin Auth failure is typed, prevents remote deletion and stays redacted", async () => {
  const rawFailure = "Admin provider exposed a private failure";
  const harness = orphanHarness({authFailure: new Error(rawFailure)});

  await assert.rejects(
    harness.handler(authed()),
    (failure) => failure.code === "internal",
  );

  assert.deepEqual(harness.calls, [
    ["verifyIdToken", ORPHAN_TOKEN],
    ["deleteAuthUser", ORPHAN_UID],
  ]);
  assert.deepEqual(harness.logs, [
    ["error", "Orphaned anonymous cleanup failed", {stage: "AUTH_USER"}],
  ]);
  assert.equal(JSON.stringify(harness.logs).includes(rawFailure), false);
});

test("callable logs contain no UID, token, payload or raw provider value", async () => {
  const harness = orphanHarness();

  const result = await harness.handler(authed({extra: "forbidden-payload"}));

  assert.deepEqual(result, {status: "ORPHANED_ANONYMOUS_ACCOUNT_DELETED"});
  const serializedLogs = JSON.stringify(harness.logs);
  assert.equal(serializedLogs.includes(ORPHAN_UID), false);
  assert.equal(serializedLogs.includes(PERMANENT_UID), false);
  assert.equal(serializedLogs.includes(ORPHAN_TOKEN), false);
  assert.equal(serializedLogs.includes("forbidden-payload"), false);
  assert.deepEqual(harness.logs, [
    ["info", "Orphaned anonymous cleanup completed", {status: "ORPHANED_ANONYMOUS_ACCOUNT_DELETED"}],
  ]);
});

function authed(data = {anonymousIdToken: ORPHAN_TOKEN}) {
  return {
    auth: {token: {}, uid: PERMANENT_UID},
    data,
  };
}

function verifiedToken({uid = ORPHAN_UID, signInProvider = "anonymous"} = {}) {
  return {sign_in_provider: signInProvider, uid};
}

function orphanHarness({
  authFailure,
  authUserMissing = false,
  failCollection,
  failCollectionOnce,
  verified = verifiedToken(),
  verifyFailure = false,
} = {}) {
  const calls = [];
  const logs = [];
  let remainingOneShotFailure = failCollectionOnce;
  const handler = createOrphanCleanupHandler({
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
      async verifyIdToken(token) {
        calls.push(["verifyIdToken", token]);
        if (verifyFailure) {
          throw Object.assign(new Error("bad token"), {code: "auth/argument-error"});
        }
        return verified;
      },
    },
    firestore: {
      async deleteCollection(uid, collection) {
        calls.push(["deleteCollection", uid, collection]);
        if (collection === failCollection || collection === remainingOneShotFailure) {
          remainingOneShotFailure = undefined;
          throw new Error("database exploded");
        }
      },
    },
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
