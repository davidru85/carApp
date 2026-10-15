import assert from "node:assert/strict";
import test from "node:test";

import {createDeleteAccountHandler} from "../lib/callable/deleteAccount.js";
import {createOrphanCleanupTicketHandler} from "../lib/callable/deleteOrphanedAnonymousAccount.js";

const ANONYMOUS_UID = "anonymous-1";
const CLEANUP_TICKET = "A".repeat(43);

// D-149, accepted as option D on 2026-10-15: the issuance/deletion interleaving is a recorded
// residual risk, not a closed one. This test pins the *current, honest* behaviour of the real
// handlers so that no document can describe the deletion flow as erasing every UID-bound
// authorization: the issuer's Admin lookup passes while the account still exists (I1), the deletion
// flow deletes the remote data (D1) and purges authorizations before any exists (D2), the issuer's
// write lands (I2), the Auth user is deleted (D3) and `deleteAccount` returns success (D4) — with the
// authorization still stored. Its only cleanup is the Firestore TTL on `expiresAt`, which has no
// proven maximum (ADR-0150, `docs/SECURITY.md`). It is not a safety guarantee; it is the opposite.

test("D-149: an issuance written after the deletion purge outlives a successful account deletion", async () => {
  const authorizations = new Map();
  const account = {value: {disabled: false, providerData: []}};
  const order = [];
  const purgeDone = deferred();
  const issuanceWritten = deferred();

  const issueTicket = createOrphanCleanupTicketHandler({
    auth: {
      async getUser(uid) {
        order.push(["I1 lookup", uid]);
        return account.value;
      },
      async deleteUser() {
        throw new Error("the issuer never deletes a user");
      },
    },
    authorizations: {
      async issue(authorization) {
        // The write is held until the deletion flow's purge has run, which is the interleaving.
        await purgeDone.promise;
        authorizations.set(authorization.ticketHash, authorization);
        order.push(["I2 write", authorization.anonymousUid]);
        issuanceWritten.resolve();
      },
      async get() {
        throw new Error("the issuer never reads an authorization");
      },
      async complete() {
        throw new Error("the issuer never completes an authorization");
      },
    },
    clock: {nowMs: () => 0},
    logger: silentLogger(),
    ticketGenerator: () => CLEANUP_TICKET,
  });

  const deleteAccount = createDeleteAccountHandler({
    auth: {
      async deleteUser(uid) {
        // The Auth user is deleted only after the issuer's write, so the lookup above was valid.
        await issuanceWritten.promise;
        account.value = null;
        order.push(["D3 delete user", uid]);
      },
    },
    firestore: {
      async deleteCollection(uid, collection) {
        order.push(["D1 delete", uid, collection]);
      },
    },
    orphanCleanupAuthorizations: {
      async purgeForUid(uid) {
        for (const [ticketHash, authorization] of authorizations) {
          if (authorization.anonymousUid === uid) {
            authorizations.delete(ticketHash);
          }
        }
        order.push(["D2 purge", uid, authorizations.size]);
        purgeDone.resolve();
      },
    },
    logger: silentLogger(),
  });

  // The issuer starts first, so its Admin lookup resolves against the still-existing account.
  const issuance = issueTicket({
    auth: {token: {firebase: {sign_in_provider: "anonymous"}}, uid: ANONYMOUS_UID},
    data: {},
  });
  const deletion = deleteAccount({
    auth: {token: {}, uid: ANONYMOUS_UID},
    data: {targetUid: ANONYMOUS_UID},
  });

  const [issued, deleted] = await Promise.all([issuance, deletion]);

  assert.deepEqual(deleted, {status: "ACCOUNT_DELETED"}, "D4: the deletion reports success");
  assert.deepEqual(issued, {cleanupTicket: CLEANUP_TICKET}, "the issuer still returns its ticket");
  assert.deepEqual(order, [
    ["I1 lookup", ANONYMOUS_UID],
    ["D1 delete", ANONYMOUS_UID, "fuelEntries"],
    ["D1 delete", ANONYMOUS_UID, "vehicles"],
    ["D2 purge", ANONYMOUS_UID, 0],
    ["I2 write", ANONYMOUS_UID],
    ["D3 delete user", ANONYMOUS_UID],
  ]);
  assert.equal(account.value, null, "the Auth user is gone");
  assert.deepEqual(
    [...authorizations.values()].map((authorization) => authorization.anonymousUid),
    [ANONYMOUS_UID],
    "one UID-bound authorization outlives the successful deletion: the accepted D-149 residual",
  );
});

function deferred() {
  let resolve;
  const promise = new Promise((settle) => {
    resolve = settle;
  });
  return {promise, resolve};
}

function silentLogger() {
  return {
    error() {},
    info() {},
  };
}
