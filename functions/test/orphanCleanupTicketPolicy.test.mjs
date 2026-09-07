import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import test from "node:test";

const REPO_ROOT = new URL("../../", import.meta.url);

test("the orphan cleanup path contains no expired-token verification fallback", () => {
  const source = readFileSync(
    new URL("functions/src/callable/deleteOrphanedAnonymousAccount.ts", REPO_ROOT),
    "utf8",
  );

  assert.equal(source.includes("anonymousIdToken"), false);
  assert.equal(source.includes("verifyExpiredAnonymousToken"), false);
  assert.equal(source.includes("securetoken@system.gserviceaccount.com"), false);
});

test("orphan cleanup authorization records have an unindexed Firestore TTL policy", () => {
  const indexes = JSON.parse(readFileSync(
    new URL("firestore/firestore.indexes.json", REPO_ROOT),
    "utf8",
  ));

  assert.deepEqual(indexes.fieldOverrides, [{
    collectionGroup: "orphanCleanupTickets",
    fieldPath: "expiresAt",
    indexes: [],
    ttl: true,
  }]);
});
