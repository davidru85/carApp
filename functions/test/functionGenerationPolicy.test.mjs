import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import test from "node:test";

const INDEX_SOURCE = new URL("../src/index.ts", import.meta.url);
const AUTH_TRIGGER_SOURCE = new URL("../src/auth/onAnonymousUserDeleted.ts", import.meta.url);

const PERMITTED_FIRST_GEN_FUNCTIONS = ["onAnonymousUserDeleted"];

test("onAnonymousUserDeleted is the only 1st gen function in the project", async () => {
  const exportedFunctions = await import(`../lib/index.js?policy=${Date.now()}`);
  const firstGenExports = Object.entries(exportedFunctions)
    .filter(([, declaration]) => declaration.__endpoint?.platform !== "gcfv2")
    .map(([name]) => name)
    .sort();

  assert.deepEqual(firstGenExports, PERMITTED_FIRST_GEN_FUNCTIONS);
  assert.equal(exportedFunctions.onAnonymousUserDeleted.__endpoint, undefined);
  assert.equal(typeof exportedFunctions.onAnonymousUserDeleted, "function");
  assert.deepEqual(Object.keys(exportedFunctions).sort(), [
    "deleteAccount",
    "deleteOrphanedAnonymousAccount",
    "onAnonymousUserDeleted",
    "stopBilling",
  ]);
  assert.equal(
    exportedFunctions.deleteOrphanedAnonymousAccount.__endpoint?.platform,
    "gcfv2",
  );
});

test("the deployment configuration declares no hidden 1st gen entry point", () => {
  const indexSource = readFileSync(INDEX_SOURCE, "utf8");
  const triggerSource = readFileSync(AUTH_TRIGGER_SOURCE, "utf8");
  const firebaseConfig = JSON.parse(readFileSync(new URL("../../../firebase.json", import.meta.url), "utf8"));

  const firstGenImports = indexSource.match(/firebase-functions\/v1/g) ?? [];
  assert.deepEqual(firstGenImports, []);

  assert.match(triggerSource, /firebase-functions\/v1/);
  assert.match(triggerSource, /auth\.user\(\)\.onDelete/);
  assert.match(triggerSource, /export const onAnonymousUserDeleted/);

  assert.equal(firebaseConfig.functions.source, "functions");
  assert.equal(firebaseConfig.functions.codebase, "default");
  assert.equal(firebaseConfig.functions.runtime, "nodejs22");
});
