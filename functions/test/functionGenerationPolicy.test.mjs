import assert from "node:assert/strict";
import {readdirSync, readFileSync} from "node:fs";
import test from "node:test";
import {fileURLToPath} from "node:url";

const REPO_ROOT = new URL("../../", import.meta.url);
const FUNCTIONS_SRC = new URL("../src/", import.meta.url);

const PERMITTED_FIRST_GEN_FUNCTIONS = ["onAnonymousUserDeleted"];

test("onAnonymousUserDeleted is the only 1st gen function in the project", () => {
  const indexSource = readFileSync(new URL("index.ts", FUNCTIONS_SRC), "utf8");
  const sourceFiles = collectSources(FUNCTIONS_SRC);

  const firstGenSources = sourceFiles.filter(({source}) =>
    source.includes("firebase-functions/v1"),
  );
  assert.deepEqual(
    firstGenSources.map(({name}) => name).sort(),
    ["auth/onAnonymousUserDeleted.ts"],
  );

  const v1TriggerSources = firstGenSources.filter(({source}) =>
    /auth\s*\r?\n?\s*\.user\(\)\s*\r?\n?\s*\.onDelete/.test(source) ||
    /functions\.auth|\.auth\.user\(/.test(source),
  );
  assert.deepEqual(
    v1TriggerSources.map(({name}) => name).sort(),
    ["auth/onAnonymousUserDeleted.ts"],
  );

  const secondGenDeclarations = sourceFiles.filter(({source}) =>
    /from\s+"firebase-functions\/v2\//.test(source),
  );
  assert.ok(secondGenDeclarations.length >= 2, "2nd gen callables remain 2nd gen");

  assert.deepEqual(
    exportedFunctionNames(indexSource),
    [
      "deleteAccount",
      "deleteOrphanedAnonymousAccount",
      "issueOrphanCleanupTicket",
      "onAnonymousUserDeleted",
      "stopBilling",
    ],
  );
  assert.deepEqual(
    firstGenModuleNames(firstGenSources),
    PERMITTED_FIRST_GEN_FUNCTIONS,
  );
});

test("the deployment configuration declares no hidden 1st gen entry point", () => {
  const triggerSource = readFileSync(
    new URL("auth/onAnonymousUserDeleted.ts", FUNCTIONS_SRC),
    "utf8",
  );
  const firebaseConfig = JSON.parse(readFileSync(new URL("firebase.json", REPO_ROOT), "utf8"));

  assert.match(triggerSource, /firebase-functions\/v1/);
  assert.match(triggerSource, /auth\s*\r?\n?\s*\.user\(\)\s*\r?\n?\s*\.onDelete/);
  assert.match(triggerSource, /export const onAnonymousUserDeleted/);

  assert.equal(firebaseConfig.functions.source, "functions");
  assert.equal(firebaseConfig.functions.codebase, "default");
  assert.equal(firebaseConfig.functions.runtime, "nodejs22");
});

function collectSources(rootUrl) {
  const entries = [];
  const visit = (directoryPath) => {
    const directoryUrl = new URL(`${directoryPath === "" ? "." : directoryPath}/`, rootUrl);
    for (const entry of readdirSync(fileURLToPath(directoryUrl), {withFileTypes: true})) {
      const relative = directoryPath === "" ? entry.name : `${directoryPath}/${entry.name}`;
      if (entry.isDirectory()) {
        visit(relative);
      } else if (entry.name.endsWith(".ts")) {
        entries.push({
          name: relative,
          source: readFileSync(new URL(relative, rootUrl), "utf8"),
        });
      }
    }
  };
  visit("");
  return entries;
}

function exportedFunctionNames(indexSource) {
  const names = [];
  for (const match of indexSource.matchAll(/export \{(\w+)\}/g)) {
    names.push(match[1]);
  }
  for (const match of indexSource.matchAll(/export const (\w+)/g)) {
    names.push(match[1]);
  }
  return names.sort();
}

function firstGenModuleNames(firstGenSources) {
  return firstGenSources
    .map(({name}) => name.replace(/\.ts$/, "").replace(/^auth\//, ""))
    .sort();
}
