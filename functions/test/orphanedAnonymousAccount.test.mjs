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

test("the real Admin SDK decoded-token shape (nested firebase claim) is accepted", async () => {
  const harness = orphanHarness({
    verified: realSdkToken({uid: ORPHAN_UID, signInProvider: "anonymous"}),
  });

  const result = await harness.handler(authed());

  assert.deepEqual(result, {status: "ORPHANED_ANONYMOUS_ACCOUNT_DELETED"});
  assert.deepEqual(harness.calls, [
    ["verifyIdToken", ORPHAN_TOKEN],
    ["deleteAuthUser", ORPHAN_UID],
    ["deleteCollection", ORPHAN_UID, "fuelEntries"],
    ["deleteCollection", ORPHAN_UID, "vehicles"],
  ]);
});

test("a token carrying only a legacy top-level sign_in_provider is rejected", async () => {
  const harness = orphanHarness({
    verified: {sign_in_provider: "anonymous", uid: ORPHAN_UID},
  });

  await assert.rejects(
    harness.handler(authed()),
    (failure) => failure.code === "failed-precondition",
  );

  assert.deepEqual(harness.calls, [["verifyIdToken", ORPHAN_TOKEN]]);
});

test("a permanent captured identity is rejected as not anonymous", async () => {
  const harness = orphanHarness({
    verified: realSdkToken({uid: ORPHAN_UID, signInProvider: "google.com"}),
  });

  await assert.rejects(
    harness.handler(authed()),
    (failure) => failure.code === "failed-precondition",
  );

  assert.deepEqual(harness.calls, [["verifyIdToken", ORPHAN_TOKEN]]);
});

test("deleting the current permanent UID is rejected before Admin deletion", async () => {
  const harness = orphanHarness({
    verified: realSdkToken({uid: PERMANENT_UID, signInProvider: "anonymous"}),
  });

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

  const result = await harness.handler(authed({anonymousIdToken: ORPHAN_TOKEN, extra: "forbidden-payload"}));

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

test("an authenticated anonymous caller is rejected before token verification or deletion", async () => {
  const harness = orphanHarness();

  await assert.rejects(
    harness.handler({
      auth: {
        token: {
          firebase: {
            sign_in_provider: "anonymous",
          },
        },
        uid: "another-anonymous-uid",
      },
      data: {anonymousIdToken: ORPHAN_TOKEN},
    }),
    (failure) => failure.code === "failed-precondition",
  );

  assert.deepEqual(harness.calls, []);
});

test("an authenticated caller without a verified permanent provider claim is rejected", async () => {
  const harness = orphanHarness();

  await assert.rejects(
    harness.handler({
      auth: {
        token: {},
        uid: PERMANENT_UID,
      },
      data: {anonymousIdToken: ORPHAN_TOKEN},
    }),
    (failure) => failure.code === "failed-precondition",
  );

  assert.deepEqual(harness.calls, []);
});

test("an Admin SDK internal verification error maps to internal and logs AUTH_USER stage", async () => {
  const harness = orphanHarness({
    verifyError: Object.assign(new Error("connection timeout"), {code: "auth/internal-error"}),
  });

  await assert.rejects(
    harness.handler(authed()),
    (failure) => failure.code === "internal",
  );

  assert.deepEqual(harness.calls, [["verifyIdToken", ORPHAN_TOKEN]]);
  assert.deepEqual(harness.logs, [
    ["error", "Orphaned anonymous cleanup failed", {stage: "AUTH_USER"}],
  ]);
});

test("a revoked or invalid token error maps to invalid-argument without internal log", async () => {
  const harness = orphanHarness({
    verifyError: Object.assign(new Error("token revoked"), {code: "auth/id-token-revoked"}),
  });

  await assert.rejects(
    harness.handler(authed()),
    (failure) => failure.code === "invalid-argument",
  );

  assert.deepEqual(harness.calls, [["verifyIdToken", ORPHAN_TOKEN]]);
  assert.deepEqual(harness.logs, []);
});

test("an expired token for an active Auth user is rejected with invalid-argument", async () => {
  const expiredToken = makeJwt({sub: ORPHAN_UID, firebase: {sign_in_provider: "anonymous"}});
  const harness = orphanHarness({
    userExistsInAuth: true,
    verifyError: Object.assign(new Error("token expired"), {code: "auth/id-token-expired"}),
  });

  await assert.rejects(
    harness.handler(authed({anonymousIdToken: expiredToken})),
    (failure) => failure.code === "invalid-argument",
  );

  assert.deepEqual(harness.calls, [
    ["verifyIdToken", expiredToken],
    ["getUser", ORPHAN_UID],
  ]);
  assert.deepEqual(harness.logs, []);
});

test("a retry with an expired anonymous token converges when the Auth account is already deleted", async () => {
  const expiredToken = makeJwt({sub: ORPHAN_UID, firebase: {sign_in_provider: "anonymous"}});
  const harness = orphanHarness({
    authUserMissing: true,
    verifyError: Object.assign(new Error("token expired"), {code: "auth/id-token-expired"}),
  });

  const result = await harness.handler(authed({anonymousIdToken: expiredToken}));

  assert.deepEqual(result, {status: "ORPHANED_ANONYMOUS_ACCOUNT_DELETED"});
  assert.deepEqual(harness.calls, [
    ["verifyIdToken", expiredToken],
    ["getUser", ORPHAN_UID],
    ["deleteCollection", ORPHAN_UID, "fuelEntries"],
    ["deleteCollection", ORPHAN_UID, "vehicles"],
  ]);
});

test("an expired token carrying a non-anonymous provider is rejected with invalid-argument", async () => {
  const expiredToken = makeJwt({sub: ORPHAN_UID, firebase: {sign_in_provider: "google.com"}});
  const harness = orphanHarness({
    authUserMissing: true,
    verifyError: Object.assign(new Error("token expired"), {code: "auth/id-token-expired"}),
  });

  await assert.rejects(
    harness.handler(authed({anonymousIdToken: expiredToken})),
    (failure) => failure.code === "invalid-argument",
  );

  assert.deepEqual(harness.calls, [["verifyIdToken", expiredToken]]);
  assert.deepEqual(harness.logs, []);
});

test("an expired token targeting the caller UID is rejected with failed-precondition", async () => {
  const expiredToken = makeJwt({sub: PERMANENT_UID, firebase: {sign_in_provider: "anonymous"}});
  const harness = orphanHarness({
    authUserMissing: true,
    verifyError: Object.assign(new Error("token expired"), {code: "auth/id-token-expired"}),
  });

  await assert.rejects(
    harness.handler(authed({anonymousIdToken: expiredToken})),
    (failure) => failure.code === "failed-precondition",
  );

  assert.deepEqual(harness.calls, [
    ["verifyIdToken", expiredToken],
    ["getUser", PERMANENT_UID],
  ]);
  assert.deepEqual(harness.logs, []);
});

test("a transient failure during user lookup on expired token retry maps to internal", async () => {
  const expiredToken = makeJwt({sub: ORPHAN_UID, firebase: {sign_in_provider: "anonymous"}});
  const harness = orphanHarness({
    getUserError: Object.assign(new Error("network timeout"), {code: "auth/internal-error"}),
    verifyError: Object.assign(new Error("token expired"), {code: "auth/id-token-expired"}),
  });

  await assert.rejects(
    harness.handler(authed({anonymousIdToken: expiredToken})),
    (failure) => failure.code === "internal",
  );

  assert.deepEqual(harness.calls, [
    ["verifyIdToken", expiredToken],
    ["getUser", ORPHAN_UID],
  ]);
  assert.deepEqual(harness.logs, [
    ["error", "Orphaned anonymous cleanup failed", {stage: "AUTH_USER"}],
  ]);
});

function authed(data = {anonymousIdToken: ORPHAN_TOKEN}) {
  return {
    auth: {
      token: {
        firebase: {
          sign_in_provider: "google.com",
        },
      },
      uid: PERMANENT_UID,
    },
    data,
  };
}

function makeJwt(payload) {
  const header = Buffer.from(JSON.stringify({alg: "RS256", kid: "test-kid"})).toString("base64url");
  const body = Buffer.from(JSON.stringify(payload)).toString("base64url");
  const sig = Buffer.from("sig").toString("base64url");
  return `${header}.${body}.${sig}`;
}

function realSdkToken({uid, signInProvider}) {
  return {
    uid,
    firebase: {
      identities: {},
      sign_in_provider: signInProvider,
    },
  };
}

function verifiedToken({uid = ORPHAN_UID, signInProvider = "anonymous"} = {}) {
  return realSdkToken({uid, signInProvider});
}

function orphanHarness({
  authFailure,
  authUserMissing = false,
  failCollection,
  failCollectionOnce,
  getUserError,
  userExistsInAuth = false,
  verified = verifiedToken(),
  verifyError,
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
      async getUser(uid) {
        calls.push(["getUser", uid]);
        if (getUserError !== undefined) {
          throw getUserError;
        }
        if (userExistsInAuth) {
          return {uid};
        }
        if (authUserMissing) {
          throw Object.assign(new Error("already gone"), {code: "auth/user-not-found"});
        }
        return {uid};
      },
      async verifyIdToken(token) {
        calls.push(["verifyIdToken", token]);
        if (verifyError !== undefined) {
          throw verifyError;
        }
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
