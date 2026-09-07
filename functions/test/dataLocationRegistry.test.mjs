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
  const declaredLocations = parseDeclaredDataLocations(contracts);

  assert.deepEqual(
    USER_DATA_LOCATIONS.firestoreCollections,
    declaredLocations.firestoreCollections,
  );
  assert.deepEqual(
    USER_DATA_LOCATIONS.storagePrefixes,
    declaredLocations.storagePrefixes,
  );
}

function parseDeclaredDataLocations(contracts) {
  const declarationMarker = "The registry is currently exactly:";
  const markerIndex = contracts.indexOf(declarationMarker);
  assert.notEqual(markerIndex, -1, "missing deletion registry declaration");

  const declaration = contracts.slice(markerIndex + declarationMarker.length);
  const registryBlock = /^\s*```text\r?\n([\s\S]*?)\r?\n```/.exec(declaration);
  assert.ok(registryBlock, "missing fenced deletion registry block");

  const firestoreCollections = [];
  let storagePrefixes;
  for (const line of registryBlock[1].split(/\r?\n/)) {
    const firestorePrefix = "Firestore collection: ";
    const storagePrefix = "Cloud Storage prefixes: ";
    if (line.startsWith(firestorePrefix)) {
      const path = line.slice(firestorePrefix.length);
      const pathMatch = /^users\/\{uid\}\/([^/]+)\/\{[^/{}]+\}$/.exec(path);
      assert.ok(pathMatch, `invalid registered Firestore path: ${path}`);
      firestoreCollections.push({collection: pathMatch[1], path});
    } else if (line.startsWith(storagePrefix)) {
      assert.equal(storagePrefixes, undefined, "duplicate Cloud Storage prefix declaration");
      const parsedPrefixes = JSON.parse(line.slice(storagePrefix.length));
      assert.equal(
        Array.isArray(parsedPrefixes) && parsedPrefixes.every((prefix) => typeof prefix === "string"),
        true,
        "Cloud Storage prefixes must be a JSON string array",
      );
      storagePrefixes = parsedPrefixes;
    } else {
      assert.fail(`unknown deletion registry declaration: ${line}`);
    }
  }

  assert.notEqual(storagePrefixes, undefined, "missing Cloud Storage prefix declaration");
  return {firestoreCollections, storagePrefixes};
}
