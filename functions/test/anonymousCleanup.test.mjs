import assert from "node:assert/strict";
import test from "node:test";

import {createAnonymousDeletionHandler} from "../lib/auth/onAnonymousUserDeleted.js";

const ORPHAN_UID = "orphan-1";

test("the native trigger delegates an eligible anonymous deletion", async () => {
  const harness = anonymousHarness();

  await harness.handler(anonymousUser(ORPHAN_UID), triggerContext());

  assert.deepEqual(harness.calls, [
    ["deleteCollection", ORPHAN_UID, "fuelEntries"],
    ["deleteCollection", ORPHAN_UID, "vehicles"],
  ]);
  assert.deepEqual(harness.logs, [
    ["info", "Anonymous cleanup invoked", {path: "NATIVE_TRIGGER"}],
  ]);
});

test("the native trigger skips a deleted user that is not anonymous", async () => {
  const harness = anonymousHarness();

  await harness.handler(
    anonymousUser("linked-1", [{providerId: "google.com"}]),
    triggerContext(),
  );

  assert.deepEqual(harness.calls, []);
  assert.deepEqual(harness.logs, [
    ["info", "Anonymous cleanup skipped", {reason: "NOT_ANONYMOUS"}],
  ]);
});

test("the native trigger skips a deleted user without a UID", async () => {
  const harness = anonymousHarness();

  await harness.handler({providerData: [], uid: undefined}, triggerContext());

  assert.deepEqual(harness.calls, []);
  assert.deepEqual(harness.logs, [
    ["info", "Anonymous cleanup skipped", {reason: "MISSING_UID"}],
  ]);
});

test("the native trigger skips a phone-only user, which is a permanent provider", async () => {
  const harness = anonymousHarness();

  await harness.handler(
    anonymousUser("phone-1", [{providerId: "phone"}]),
    triggerContext(),
  );

  assert.deepEqual(harness.calls, []);
  assert.deepEqual(harness.logs, [
    ["info", "Anonymous cleanup skipped", {reason: "NOT_ANONYMOUS"}],
  ]);
});

test("native trigger redelivery after a successful deletion succeeds idempotently", async () => {
  const harness = anonymousHarness();

  await harness.handler(anonymousUser(ORPHAN_UID), triggerContext());
  await harness.handler(anonymousUser(ORPHAN_UID), triggerContext());

  assert.deepEqual(harness.calls, [
    ["deleteCollection", ORPHAN_UID, "fuelEntries"],
    ["deleteCollection", ORPHAN_UID, "vehicles"],
    ["deleteCollection", ORPHAN_UID, "fuelEntries"],
    ["deleteCollection", ORPHAN_UID, "vehicles"],
  ]);
  assert.deepEqual(harness.logs, [
    ["info", "Anonymous cleanup invoked", {path: "NATIVE_TRIGGER"}],
    ["info", "Anonymous cleanup invoked", {path: "NATIVE_TRIGGER"}],
  ]);
});

test("the native trigger retries a failed deletion on redelivery", async () => {
  const harness = anonymousHarness({failCollectionOnce: "vehicles"});

  await assert.rejects(
    harness.handler(anonymousUser(ORPHAN_UID), triggerContext()),
    (failure) => failure.code === "internal",
  );
  await harness.handler(anonymousUser(ORPHAN_UID), triggerContext());

  assert.deepEqual(harness.calls, [
    ["deleteCollection", ORPHAN_UID, "fuelEntries"],
    ["deleteCollection", ORPHAN_UID, "vehicles"],
    ["deleteCollection", ORPHAN_UID, "fuelEntries"],
    ["deleteCollection", ORPHAN_UID, "vehicles"],
  ]);
  assert.deepEqual(harness.logs, [
    ["error", "Anonymous cleanup failed", {path: "NATIVE_TRIGGER"}],
    ["info", "Anonymous cleanup invoked", {path: "NATIVE_TRIGGER"}],
  ]);
});

test("native trigger logs contain no UID, token, event payload or raw failure", async () => {
  const secretToken = "secret-trigger-token";
  const harness = anonymousHarness({failCollection: "fuelEntries"});

  await assert.rejects(
    harness.handler(anonymousUser(ORPHAN_UID), {
      auth: {uid: ORPHAN_UID},
      authType: "USER",
      eventId: "event-1",
      rawPayload: secretToken,
      resource: {name: `users/${ORPHAN_UID}`},
    }),
  );

  const serializedLogs = JSON.stringify(harness.logs);
  assert.equal(serializedLogs.includes(ORPHAN_UID), false);
  assert.equal(serializedLogs.includes(secretToken), false);
  assert.equal(serializedLogs.includes("database exploded"), false);
  assert.deepEqual(harness.logs, [
    ["error", "Anonymous cleanup failed", {path: "NATIVE_TRIGGER"}],
  ]);
});

test("trigger deletion while the callable is mid-deletion converges idempotently", async () => {
  const harness = anonymousHarness({gateCollection: "vehicles"});

  const firstPass = harness.handler(anonymousUser(ORPHAN_UID), triggerContext());
  await harness.waitForGateEntry();
  const overlapPass = harness.handler(anonymousUser(ORPHAN_UID), triggerContext());
  harness.releaseGate();
  await Promise.all([firstPass, overlapPass]);

  const deletions = harness.calls.filter(([name]) => name === "deleteCollection");
  for (const [, uid, collection] of deletions) {
    assert.equal(uid, ORPHAN_UID);
    assert.ok(collection === "fuelEntries" || collection === "vehicles");
  }
  assert.equal(harness.calls.filter(([, , c]) => c === "vehicles").length, 2);
});

function anonymousUser(uid, providerData = []) {
  return {
    uid,
    providerData,
  };
}

function triggerContext() {
  return {
    auth: {},
    eventId: "event-1",
  };
}

function anonymousHarness({
  failCollection,
  failCollectionOnce,
  gateCollection,
} = {}) {
  const calls = [];
  const logs = [];
  let remainingOneShotFailure = failCollectionOnce;
  let gateWaiters = [];
  let gateEntered;
  const handler = createAnonymousDeletionHandler({
    firestore: {
      async deleteCollection(uid, collection) {
        calls.push(["deleteCollection", uid, collection]);
        if (collection === gateCollection && gateEntered === undefined) {
          gateEntered = new Promise((resolve) => gateWaiters.push(resolve));
          await gateEntered;
        }
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
  return {
    calls,
    handler,
    logs,
    async waitForGateEntry() {
      for (let attempt = 0; attempt < 100 && gateEntered === undefined; attempt += 1) {
        await new Promise((resolve) => setImmediate(resolve));
      }
    },
    releaseGate() {
      for (const resolve of gateWaiters) resolve();
      gateWaiters = [];
    },
  };
}
