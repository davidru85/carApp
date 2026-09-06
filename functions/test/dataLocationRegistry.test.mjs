import assert from "node:assert/strict";
import {readFile} from "node:fs/promises";
import test from "node:test";

import {USER_DATA_LOCATIONS} from "../lib/deletion/dataLocationRegistry.js";

test("the deletion registry exactly matches the closed remote data-location schema", async () => {
  const contractsUrl = new URL("../../docs/CONTRACTS.md", import.meta.url);
  const contracts = await readFile(contractsUrl, "utf8");
  const declaredCollections = new Set();
  const pathPattern = /users\/\{uid\}\/([A-Za-z]+)\/\{(?:vehicleId|entryId)\}/g;

  for (const match of contracts.matchAll(pathPattern)) {
    declaredCollections.add(match[1]);
  }

  assert.deepEqual(
    USER_DATA_LOCATIONS.firestoreCollections.map((location) => location.collection),
    ["fuelEntries", "vehicles"],
  );
  assert.deepEqual(
    new Set(USER_DATA_LOCATIONS.firestoreCollections.map((location) => location.collection)),
    declaredCollections,
  );
  assert.deepEqual(USER_DATA_LOCATIONS.storagePrefixes, []);
});
