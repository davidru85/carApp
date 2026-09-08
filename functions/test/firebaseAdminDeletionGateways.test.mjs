import assert from "node:assert/strict";
import test from "node:test";

import {FirebaseAdminAuthDeletionGateway} from "../lib/deletion/firebaseAdminDeletionGateways.js";

test("the Admin Auth gateway forwards disabled and providerData of the resolved record", async () => {
  const getUserCalls = [];
  const records = new Map([
    ["enabled-uid", {disabled: false, providerData: []}],
    ["disabled-uid", {disabled: true, providerData: [{providerId: "google.com"}]}],
  ]);
  const gateway = new FirebaseAdminAuthDeletionGateway({
    async getUser(uid) {
      getUserCalls.push(uid);
      return records.get(uid);
    },
  });

  assert.deepEqual(await gateway.getUser("enabled-uid"), {disabled: false, providerData: []});
  assert.deepEqual(await gateway.getUser("disabled-uid"), {
    disabled: true,
    providerData: [{providerId: "google.com"}],
  });
  assert.deepEqual(getUserCalls, ["enabled-uid", "disabled-uid"]);
});

test("the Admin Auth gateway maps only auth/user-not-found to null", async () => {
  const notFound = new Error("user not found");
  notFound.code = "auth/user-not-found";
  const notFoundGateway = new FirebaseAdminAuthDeletionGateway({
    async getUser() {
      throw notFound;
    },
  });
  assert.equal(await notFoundGateway.getUser("missing-uid"), null);

  const quotaExceeded = new Error("quota exceeded");
  quotaExceeded.code = "auth/too-many-requests";
  const quotaGateway = new FirebaseAdminAuthDeletionGateway({
    async getUser() {
      throw quotaExceeded;
    },
  });
  await assert.rejects(
    quotaGateway.getUser("throttled-uid"),
    (failure) => failure === quotaExceeded,
  );
});