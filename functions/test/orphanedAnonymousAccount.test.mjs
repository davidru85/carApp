import assert from "node:assert/strict";
import crypto from "node:crypto";
import test from "node:test";

import {
  canIssueOrphanCleanupTicket,
  isAnonymousAuthUser,
} from "../lib/auth/anonymousUserEligibility.js";
import {
  createOrphanCleanupHandler,
  createOrphanCleanupTicketHandler,
} from "../lib/callable/deleteOrphanedAnonymousAccount.js";

const PERMANENT_UID = "permanent-1";
const ORPHAN_UID = "orphan-1";
const CLEANUP_TICKET = "A".repeat(43);
const OTHER_TICKET = "B".repeat(43);
const NOW_MS = Date.UTC(2026, 8, 7, 12, 0, 0);
const THIRTY_DAYS_MS = 30 * 24 * 60 * 60 * 1000;

test("an authenticated anonymous session receives an opaque cleanup ticket bound to its UID", async () => {
  const harness = ticketHarness();

  const result = await harness.handler(anonymousRequest());

  assert.deepEqual(result, {cleanupTicket: CLEANUP_TICKET});
  assert.deepEqual(harness.calls, [
    ["getUser", ORPHAN_UID],
    ["issueAuthorization", {
      anonymousUid: ORPHAN_UID,
      expiresAtMs: NOW_MS + THIRTY_DAYS_MS,
      status: "PENDING",
      ticketHash: ticketHash(CLEANUP_TICKET),
    }],
  ]);
  assert.equal(JSON.stringify(harness.calls).includes(CLEANUP_TICKET), false);
});

test("ticket issuance rejects stale anonymous claims once the account has been linked", async () => {
  const harness = ticketHarness({
    callerUser: {disabled: false, providerData: [{providerId: "google.com"}]},
  });

  await assert.rejects(
    harness.handler(anonymousRequest()),
    (failure) => failure.code === "failed-precondition",
  );

  assert.deepEqual(harness.calls, [["getUser", ORPHAN_UID]]);
});

test("ticket issuance rejects stale anonymous claims for a disabled account", async () => {
  const harness = ticketHarness({callerUser: {disabled: true, providerData: []}});

  await assert.rejects(
    harness.handler(anonymousRequest()),
    (failure) => failure.code === "failed-precondition",
  );

  assert.deepEqual(harness.calls, [["getUser", ORPHAN_UID]]);
});

test("ticket issuance rejects stale anonymous claims for a deleted account", async () => {
  const harness = ticketHarness({callerUser: null});

  await assert.rejects(
    harness.handler(anonymousRequest()),
    (failure) => failure.code === "failed-precondition",
  );

  assert.deepEqual(harness.calls, [["getUser", ORPHAN_UID]]);
});

test("ticket issuance rejects an anonymous snapshot whose disabled state is unknown", async () => {
  // The Admin record is the only current answer, and eligibility is a positive fact: a snapshot
  // that does not carry an explicit `disabled: false` value is not known to be enabled, so the
  // issuer must fail closed instead of treating an absent value as enabled.
  const harness = ticketHarness({callerUser: {providerData: []}});

  await assert.rejects(
    harness.handler(anonymousRequest()),
    (failure) => failure.code === "failed-precondition",
  );

  assert.deepEqual(harness.calls, [["getUser", ORPHAN_UID]]);
});

// D-150: the three interleavings D-148 cannot cover. `auth.getUser` and the Firestore
// authorization write are separate operations on two services with no shared transaction, so an
// account can move between them. These tests pin the *current, honest* behaviour — an authorization
// is created against a snapshot that was eligible when it was read and is not any more — so that no
// document can claim D-148 prevents it. They are not a safety guarantee; they are the opposite.

test("D-150: an account linked between the Admin lookup and the write still gets an authorization", async () => {
  const harness = ticketHarness({
    transitionBeforeWrite: {disabled: false, providerData: [{providerId: "password"}]},
  });

  const result = await harness.handler(anonymousRequest());

  assert.deepEqual(result, {cleanupTicket: CLEANUP_TICKET});
  assert.deepEqual(harness.calls, [
    ["getUser", ORPHAN_UID],
    ["issueAuthorization", {
      anonymousUid: ORPHAN_UID,
      expiresAtMs: NOW_MS + THIRTY_DAYS_MS,
      status: "PENDING",
      ticketHash: ticketHash(CLEANUP_TICKET),
    }],
  ]);
  // The record is no longer anonymous once the write has committed, yet the UID-bound
  // authorization exists. D-142 refuses the destructive stage at consumption; it does not remove
  // this record.
  assert.equal(canIssueOrphanCleanupTicket(harness.liveAccount.value), false);
});

test("D-150: an account disabled between the Admin lookup and the write still gets an authorization", async () => {
  const harness = ticketHarness({
    transitionBeforeWrite: {disabled: true, providerData: []},
  });

  const result = await harness.handler(anonymousRequest());

  assert.deepEqual(result, {cleanupTicket: CLEANUP_TICKET});
  assert.deepEqual(harness.calls[1][0], "issueAuthorization");
  // The account stays anonymous, so D-142 consumption revalidation does not reject it either.
  assert.equal(isAnonymousAuthUser(harness.liveAccount.value), true);
  assert.equal(canIssueOrphanCleanupTicket(harness.liveAccount.value), false);
});

test("D-150: an account deleted between the Admin lookup and the write still gets an authorization", async () => {
  const harness = ticketHarness({transitionBeforeWrite: null});

  const result = await harness.handler(anonymousRequest());

  assert.deepEqual(result, {cleanupTicket: CLEANUP_TICKET});
  assert.deepEqual(harness.calls[1][0], "issueAuthorization");
  assert.equal(harness.liveAccount.value, null);
  assert.equal(canIssueOrphanCleanupTicket(harness.liveAccount.value), false);
});

test("D-148 still fails closed when the account is already ineligible at lookup time", async () => {
  for (const callerUser of [
    {disabled: false, providerData: [{providerId: "password"}]},
    {disabled: true, providerData: []},
    {providerData: []},
    null,
  ]) {
    const harness = ticketHarness({callerUser});

    await assert.rejects(
      harness.handler(anonymousRequest()),
      (error) => error.code === "failed-precondition",
    );

    assert.deepEqual(harness.calls, [["getUser", ORPHAN_UID]]);
  }
});

test("ticket issuance maps an Admin lookup failure to internal with a redacted stage", async () => {
  const harness = ticketHarness({lookupError: new Error(`admin exploded for ${ORPHAN_UID}`)});

  await assert.rejects(
    harness.handler(anonymousRequest()),
    (failure) => failure.code === "internal",
  );

  assert.deepEqual(harness.calls, [["getUser", ORPHAN_UID]]);
  assert.deepEqual(harness.logs, [[
    "error",
    "Orphan cleanup ticket issuance failed",
    {stage: "AUTH_USER"},
  ]]);
});

test("a rejected issuance leaks no UID, token, payload or raw failure", async () => {
  const secretToken = "secret-issuer-token";
  const harness = ticketHarness({lookupError: new Error(`admin exploded for ${ORPHAN_UID}`)});

  const rejection = await harness.handler({
    auth: {
      token: {firebase: {sign_in_provider: "anonymous"}, secret: secretToken},
      uid: ORPHAN_UID,
    },
    data: {secret: secretToken},
  }).then(() => undefined, (failure) => failure);

  const serialized = [
    JSON.stringify(harness.logs),
    rejection?.message,
    rejection?.stack,
    JSON.stringify(rejection ?? null),
  ].join("|");
  assert.equal(serialized.includes(ORPHAN_UID), false);
  assert.equal(serialized.includes(secretToken), false);
  assert.equal(serialized.includes("admin exploded"), false);
});

test("ticket issuance rejects an unauthenticated caller before persistence", async () => {
  const harness = ticketHarness();

  await assert.rejects(
    harness.handler({auth: undefined, data: {}}),
    (failure) => failure.code === "unauthenticated",
  );

  assert.deepEqual(harness.calls, []);
});

test("ticket issuance rejects a permanent caller before persistence", async () => {
  const harness = ticketHarness();

  await assert.rejects(
    harness.handler(permanentRequest({})),
    (failure) => failure.code === "failed-precondition",
  );

  assert.deepEqual(harness.calls, []);
});

test("ticket persistence failure maps to internal with a redacted authorization-stage log", async () => {
  const rawFailure = "private Firestore failure";
  const harness = ticketHarness({issueError: new Error(rawFailure)});

  await assert.rejects(
    harness.handler(anonymousRequest({secretPayload: "forbidden-payload"})),
    (failure) => failure.code === "internal",
  );

  assert.deepEqual(harness.logs, [[
    "error",
    "Orphan cleanup ticket issuance failed",
    {stage: "AUTHORIZATION"},
  ]]);
  const serializedLogs = JSON.stringify(harness.logs);
  assert.equal(serializedLogs.includes(ORPHAN_UID), false);
  assert.equal(serializedLogs.includes(CLEANUP_TICKET), false);
  assert.equal(serializedLogs.includes("forbidden-payload"), false);
  assert.equal(serializedLogs.includes(rawFailure), false);
});

test("successful ticket issuance logs only the closed status", async () => {
  const harness = ticketHarness();

  await harness.handler(anonymousRequest({secretPayload: "forbidden-payload"}));

  assert.deepEqual(harness.logs, [[
    "info",
    "Orphan cleanup ticket issued",
    {status: "ORPHAN_CLEANUP_TICKET_ISSUED"},
  ]]);
  const serializedLogs = JSON.stringify(harness.logs);
  assert.equal(serializedLogs.includes(ORPHAN_UID), false);
  assert.equal(serializedLogs.includes(CLEANUP_TICKET), false);
  assert.equal(serializedLogs.includes("forbidden-payload"), false);
});

test("a permanent caller uses a valid stored ticket to delete only its bound anonymous account", async () => {
  const harness = cleanupHarness();

  const result = await harness.handler(permanentRequest({cleanupTicket: CLEANUP_TICKET}));

  assert.deepEqual(result, {status: "ORPHANED_ANONYMOUS_ACCOUNT_DELETED"});
  assert.deepEqual(harness.calls, [
    ["getAuthorization", ticketHash(CLEANUP_TICKET)],
    ["getAuthUser", ORPHAN_UID],
    ["deleteAuthUser", ORPHAN_UID],
    ["deleteCollection", ORPHAN_UID, "fuelEntries"],
    ["deleteCollection", ORPHAN_UID, "vehicles"],
    ["completeAuthorization", ticketHash(CLEANUP_TICKET)],
  ]);
});

test("a ticket bound to an account that is now permanent is rejected before deletion", async () => {
  const harness = cleanupHarness({providerData: [{providerId: "google.com"}]});

  await assert.rejects(
    harness.handler(permanentRequest({cleanupTicket: CLEANUP_TICKET})),
    (failure) => failure.code === "failed-precondition",
  );

  assert.deepEqual(harness.calls, [
    ["getAuthorization", ticketHash(CLEANUP_TICKET)],
    ["getAuthUser", ORPHAN_UID],
  ]);
});

test("a ticket bound to a phone-only account is rejected before deletion", async () => {
  const harness = cleanupHarness({providerData: [{providerId: "phone"}]});

  await assert.rejects(
    harness.handler(permanentRequest({cleanupTicket: CLEANUP_TICKET})),
    (failure) => failure.code === "failed-precondition",
  );

  assert.deepEqual(harness.calls, [
    ["getAuthorization", ticketHash(CLEANUP_TICKET)],
    ["getAuthUser", ORPHAN_UID],
  ]);
});

test("cleanup rejects an unauthenticated caller before reading a ticket", async () => {
  const harness = cleanupHarness();

  await assert.rejects(
    harness.handler({auth: undefined, data: {cleanupTicket: CLEANUP_TICKET}}),
    (failure) => failure.code === "unauthenticated",
  );

  assert.deepEqual(harness.calls, []);
});

test("cleanup rejects an anonymous caller before reading a ticket", async () => {
  const harness = cleanupHarness();

  await assert.rejects(
    harness.handler(anonymousRequest({cleanupTicket: CLEANUP_TICKET})),
    (failure) => failure.code === "failed-precondition",
  );

  assert.deepEqual(harness.calls, []);
});

test("cleanup rejects a caller without a verified permanent provider before reading a ticket", async () => {
  const harness = cleanupHarness();

  await assert.rejects(
    harness.handler({auth: {token: {}, uid: PERMANENT_UID}, data: {cleanupTicket: CLEANUP_TICKET}}),
    (failure) => failure.code === "failed-precondition",
  );

  assert.deepEqual(harness.calls, []);
});

test("cleanup rejects malformed tickets and the superseded anonymousIdToken payload", async () => {
  for (const data of [
    {},
    {cleanupTicket: "short"},
    {cleanupTicket: `${"A".repeat(42)}!`},
    {anonymousIdToken: "legacy-token"},
  ]) {
    const harness = cleanupHarness();
    await assert.rejects(
      harness.handler(permanentRequest(data)),
      (failure) => failure.code === "invalid-argument",
    );
    assert.deepEqual(harness.calls, []);
  }
});

test("an unknown cleanup ticket is rejected without revealing whether an account exists", async () => {
  const harness = cleanupHarness({authorization: null});

  await assert.rejects(
    harness.handler(permanentRequest({cleanupTicket: OTHER_TICKET})),
    (failure) => failure.code === "invalid-argument",
  );

  assert.deepEqual(harness.calls, [["getAuthorization", ticketHash(OTHER_TICKET)]]);
  assert.deepEqual(harness.logs, []);
});

test("an expired cleanup ticket is rejected before any destructive operation", async () => {
  const harness = cleanupHarness({expiresAtMs: NOW_MS});

  await assert.rejects(
    harness.handler(permanentRequest({cleanupTicket: CLEANUP_TICKET})),
    (failure) => failure.code === "invalid-argument",
  );

  assert.deepEqual(harness.calls, [["getAuthorization", ticketHash(CLEANUP_TICKET)]]);
});

test("a ticket bound to the current permanent UID is rejected before deletion", async () => {
  const harness = cleanupHarness({anonymousUid: PERMANENT_UID});

  await assert.rejects(
    harness.handler(permanentRequest({cleanupTicket: CLEANUP_TICKET})),
    (failure) => failure.code === "failed-precondition",
  );

  assert.deepEqual(harness.calls, [["getAuthorization", ticketHash(CLEANUP_TICKET)]]);
});

test("a completed ticket returns idempotent success without repeating deletion", async () => {
  const harness = cleanupHarness({status: "COMPLETED"});

  const result = await harness.handler(permanentRequest({cleanupTicket: CLEANUP_TICKET}));

  assert.deepEqual(result, {status: "ORPHANED_ANONYMOUS_ACCOUNT_DELETED"});
  assert.deepEqual(harness.calls, [["getAuthorization", ticketHash(CLEANUP_TICKET)]]);
});

test("a missing anonymous Auth user continues with direct remote deletion", async () => {
  const harness = cleanupHarness({authUserMissing: true});

  const result = await harness.handler(permanentRequest({cleanupTicket: CLEANUP_TICKET}));

  assert.deepEqual(result, {status: "ORPHANED_ANONYMOUS_ACCOUNT_DELETED"});
  assert.deepEqual(harness.calls, [
    ["getAuthorization", ticketHash(CLEANUP_TICKET)],
    ["getAuthUser", ORPHAN_UID],
    ["deleteCollection", ORPHAN_UID, "fuelEntries"],
    ["deleteCollection", ORPHAN_UID, "vehicles"],
    ["completeAuthorization", ticketHash(CLEANUP_TICKET)],
  ]);
});

test("an Auth lookup failure maps to internal and prevents every destructive stage", async () => {
  const harness = cleanupHarness({getAuthUserError: new Error("private Auth lookup failure")});

  await assert.rejects(
    harness.handler(permanentRequest({cleanupTicket: CLEANUP_TICKET})),
    (failure) => failure.code === "internal",
  );

  assert.deepEqual(harness.calls, [
    ["getAuthorization", ticketHash(CLEANUP_TICKET)],
    ["getAuthUser", ORPHAN_UID],
  ]);
  assert.deepEqual(harness.logs, [[
    "error",
    "Orphaned anonymous cleanup failed",
    {stage: "AUTH_USER"},
  ]]);
});

test("an authorization lookup failure maps to internal and performs no deletion", async () => {
  const harness = cleanupHarness({getError: new Error("private lookup failure")});

  await assert.rejects(
    harness.handler(permanentRequest({cleanupTicket: CLEANUP_TICKET})),
    (failure) => failure.code === "internal",
  );

  assert.deepEqual(harness.calls, [["getAuthorization", ticketHash(CLEANUP_TICKET)]]);
  assert.deepEqual(harness.logs, [[
    "error",
    "Orphaned anonymous cleanup failed",
    {stage: "AUTHORIZATION"},
  ]]);
});

test("an Auth deletion failure maps to internal and prevents remote deletion", async () => {
  const harness = cleanupHarness({authError: new Error("private Auth failure")});

  await assert.rejects(
    harness.handler(permanentRequest({cleanupTicket: CLEANUP_TICKET})),
    (failure) => failure.code === "internal",
  );

  assert.deepEqual(harness.calls, [
    ["getAuthorization", ticketHash(CLEANUP_TICKET)],
    ["getAuthUser", ORPHAN_UID],
    ["deleteAuthUser", ORPHAN_UID],
  ]);
  assert.deepEqual(harness.logs, [[
    "error",
    "Orphaned anonymous cleanup failed",
    {stage: "AUTH_USER"},
  ]]);
});

test("a remote-data failure leaves the ticket pending for a full retry", async () => {
  const harness = cleanupHarness({failCollectionOnce: "vehicles"});

  await assert.rejects(
    harness.handler(permanentRequest({cleanupTicket: CLEANUP_TICKET})),
    (failure) => failure.code === "internal",
  );
  const result = await harness.handler(permanentRequest({cleanupTicket: CLEANUP_TICKET}));

  assert.deepEqual(result, {status: "ORPHANED_ANONYMOUS_ACCOUNT_DELETED"});
  assert.equal(harness.calls.filter(([name]) => name === "getAuthorization").length, 2);
  assert.equal(harness.calls.filter(([name]) => name === "completeAuthorization").length, 1);
});

test("a completion-write failure leaves deletion safely retryable", async () => {
  const harness = cleanupHarness({completeErrorOnce: new Error("private completion failure")});

  await assert.rejects(
    harness.handler(permanentRequest({cleanupTicket: CLEANUP_TICKET})),
    (failure) => failure.code === "internal",
  );
  const result = await harness.handler(permanentRequest({cleanupTicket: CLEANUP_TICKET}));

  assert.deepEqual(result, {status: "ORPHANED_ANONYMOUS_ACCOUNT_DELETED"});
  assert.equal(harness.calls.filter(([name]) => name === "deleteAuthUser").length, 2);
  assert.equal(harness.calls.filter(([name]) => name === "completeAuthorization").length, 2);
});

test("cleanup logs contain no UID, ticket, request payload or raw provider failure", async () => {
  const rawFailure = "private Firestore failure";
  const harness = cleanupHarness({failCollection: "fuelEntries", rawFailure});

  await assert.rejects(
    harness.handler(permanentRequest({cleanupTicket: CLEANUP_TICKET, extra: "forbidden-payload"})),
    (failure) => failure.code === "internal",
  );

  const serializedLogs = JSON.stringify(harness.logs);
  assert.equal(serializedLogs.includes(ORPHAN_UID), false);
  assert.equal(serializedLogs.includes(PERMANENT_UID), false);
  assert.equal(serializedLogs.includes(CLEANUP_TICKET), false);
  assert.equal(serializedLogs.includes("forbidden-payload"), false);
  assert.equal(serializedLogs.includes(rawFailure), false);
  assert.deepEqual(harness.logs, [[
    "error",
    "Orphaned anonymous cleanup failed",
    {stage: "REMOTE_DATA"},
  ]]);
});

function ticketHarness({
  issueError,
  lookupError,
  callerUser = {disabled: false, providerData: []},
  transitionBeforeWrite,
} = {}) {
  const calls = [];
  const logs = [];
  // `liveAccount` models the Firebase Auth record as it exists at each instant, so a test can move
  // the account between the Admin lookup and the Firestore write and observe what the handler does.
  const liveAccount = {value: callerUser};
  const handler = createOrphanCleanupTicketHandler({
    auth: {
      async getUser(uid) {
        calls.push(["getUser", uid]);
        if (lookupError !== undefined) {
          throw lookupError;
        }
        return liveAccount.value;
      },
    },
    authorizations: {
      async issue(authorization) {
        // The transition lands after `getUser` has resolved and before the authorization write
        // commits, which is exactly the window `D-148` cannot cover (`D-150`).
        if (transitionBeforeWrite !== undefined) {
          liveAccount.value = transitionBeforeWrite;
        }
        calls.push(["issueAuthorization", authorization]);
        if (issueError !== undefined) {
          throw issueError;
        }
      },
    },
    clock: {nowMs: () => NOW_MS},
    logger: testLogger(logs),
    ticketGenerator: () => CLEANUP_TICKET,
  });
  return {calls, handler, liveAccount, logs};
}

function cleanupHarness({
  anonymousUid = ORPHAN_UID,
  authError,
  authorization = undefined,
  authUserMissing = false,
  completeErrorOnce,
  expiresAtMs = NOW_MS + THIRTY_DAYS_MS,
  failCollection,
  failCollectionOnce,
  getAuthUserError,
  getError,
  providerData = [],
  rawFailure = "database exploded",
  status = "PENDING",
} = {}) {
  const calls = [];
  const logs = [];
  let remainingCompleteError = completeErrorOnce;
  let remainingCollectionFailure = failCollectionOnce;
  const resolvedAuthorization = authorization === undefined ? {
    anonymousUid,
    expiresAtMs,
    status,
    ticketHash: ticketHash(CLEANUP_TICKET),
  } : authorization;
  const handler = createOrphanCleanupHandler({
    auth: {
      async getUser(uid) {
        calls.push(["getAuthUser", uid]);
        if (getAuthUserError !== undefined) {
          throw getAuthUserError;
        }
        if (authUserMissing) {
          return null;
        }
        return {providerData};
      },
      async deleteUser(uid) {
        calls.push(["deleteAuthUser", uid]);
        if (authError !== undefined) {
          throw authError;
        }
      },
    },
    authorizations: {
      async complete(hash) {
        calls.push(["completeAuthorization", hash]);
        if (remainingCompleteError !== undefined) {
          const failure = remainingCompleteError;
          remainingCompleteError = undefined;
          throw failure;
        }
      },
      async get(hash) {
        calls.push(["getAuthorization", hash]);
        if (getError !== undefined) {
          throw getError;
        }
        return resolvedAuthorization === null ? null : {...resolvedAuthorization, ticketHash: hash};
      },
      async issue() {
        assert.fail("cleanup must not issue an authorization");
      },
    },
    clock: {nowMs: () => NOW_MS},
    firestore: {
      async deleteCollection(uid, collection) {
        calls.push(["deleteCollection", uid, collection]);
        if (collection === failCollection || collection === remainingCollectionFailure) {
          remainingCollectionFailure = undefined;
          throw new Error(rawFailure);
        }
      },
    },
    logger: testLogger(logs),
  });
  return {calls, handler, logs};
}

function anonymousRequest(data = {}) {
  return {
    auth: {
      token: {firebase: {sign_in_provider: "anonymous"}},
      uid: ORPHAN_UID,
    },
    data,
  };
}

function permanentRequest(data) {
  return {
    auth: {
      token: {firebase: {sign_in_provider: "google.com"}},
      uid: PERMANENT_UID,
    },
    data,
  };
}

function ticketHash(ticket) {
  return crypto.createHash("sha256").update(ticket, "utf8").digest("hex");
}

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
