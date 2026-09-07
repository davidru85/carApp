import assert from "node:assert/strict";
import {readFile} from "node:fs/promises";
import test from "node:test";

import {USER_DATA_LOCATIONS} from "../lib/deletion/dataLocationRegistry.js";

test("the deletion registry exactly matches the closed remote data-location schema", async () => {
  const contracts = await readContracts();

  assertRegistryMatchesContract(contracts);
});

test("the parity check detects any omitted owner collection placeholder", async () => {
  const contracts = await readContracts();
  const expandedContracts = contracts.replace(
    "Firestore collection: users/{uid}/vehicles/{vehicleId}",
    [
      "Firestore collection: users/{uid}/vehicles/{vehicleId}",
      "Firestore collection: users/{uid}/reminders/{reminderId}",
    ].join("\n"),
  );

  assert.throws(() => assertRegistryMatchesContract(expandedContracts));
});

test("the parity check detects an omitted Cloud Storage prefix", async () => {
  const contracts = await readContracts();
  const expandedContracts = contracts.replace(
    "Cloud Storage prefixes: []",
    'Cloud Storage prefixes: ["users/{uid}/receipts/"]',
  );

  assert.throws(() => assertRegistryMatchesContract(expandedContracts));
});

function readContracts() {
  const contractsUrl = new URL("../../docs/CONTRACTS.md", import.meta.url);
  return readFile(contractsUrl, "utf8");
}

function assertRegistryMatchesContract(contracts) {
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
}
