import assert from "node:assert/strict";
import {readFile} from "node:fs/promises";
import test from "node:test";

import * as dataLocationRegistry from "../lib/deletion/dataLocationRegistry.js";

const {USER_DATA_LOCATIONS} = dataLocationRegistry;

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

test("the internal server-only registry exactly matches the contract exclusion list", async () => {
  const contracts = await readContracts();
  const declaredLocations = parseDeclaredInternalDataLocations(contracts);

  assert.deepEqual(
    dataLocationRegistry.INTERNAL_SERVER_DATA_LOCATIONS,
    declaredLocations,
  );
  assert.equal(
    USER_DATA_LOCATIONS.firestoreCollections.some(({path}) =>
      declaredLocations.firestoreCollections.some((internal) => internal.path === path)),
    false,
  );
});

test("the internal parity check detects an undeclared server-only collection", async () => {
  const contracts = await readContracts();
  const expandedContracts = contracts.replace(
    "Internal Firestore collection: orphanCleanupTickets/{ticketHash}",
    [
      "Internal Firestore collection: orphanCleanupTickets/{ticketHash}",
      "Internal Firestore collection: internalAudit/{entryId}",
    ].join("\n"),
  );

  assert.throws(() => assertInternalRegistryMatchesContract(expandedContracts));
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

function assertInternalRegistryMatchesContract(contracts) {
  assert.deepEqual(
    dataLocationRegistry.INTERNAL_SERVER_DATA_LOCATIONS,
    parseDeclaredInternalDataLocations(contracts),
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

function parseDeclaredInternalDataLocations(contracts) {
  const declarationMarker = "The internal server-only collection registry is exactly:";
  const markerIndex = contracts.indexOf(declarationMarker);
  assert.notEqual(markerIndex, -1, "missing internal server-only registry declaration");

  const declaration = contracts.slice(markerIndex + declarationMarker.length);
  const registryBlock = /^\s*```text\r?\n([\s\S]*?)\r?\n```/.exec(declaration);
  assert.ok(registryBlock, "missing internal server-only registry block");

  const firestoreCollections = registryBlock[1].split(/\r?\n/).map((line) => {
    const prefix = "Internal Firestore collection: ";
    assert.equal(line.startsWith(prefix), true, `unknown internal registry declaration: ${line}`);
    const path = line.slice(prefix.length);
    const pathMatch = /^([^/]+)\/\{[^/{}]+\}$/.exec(path);
    assert.ok(pathMatch, `invalid internal Firestore path: ${path}`);
    return {collection: pathMatch[1], path};
  });

  return {firestoreCollections};
}
