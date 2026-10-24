import assert from "node:assert/strict";
import {readdirSync, readFileSync} from "node:fs";
import test from "node:test";
import {fileURLToPath} from "node:url";

// D-247: production deploys only the `default` codebase, so it MUST NOT contain the D-66 cutoff.
const REPO_ROOT = new URL("../../", import.meta.url);
const FUNCTIONS_SRC = new URL("../src/", import.meta.url);
const PRODUCT_FUNCTIONS = [
  "deleteAccount",
  "deleteOrphanedAnonymousAccount",
  "issueOrphanCleanupTicket",
  "onAnonymousUserDeleted",
];

test("the product codebase exports exactly the four product functions", async () => {
  const exported = await import(`../lib/index.js?deploySet=${Date.now()}`);

  assert.deepEqual(Object.keys(exported).sort(), PRODUCT_FUNCTIONS);
});

test("the product codebase source never references the development billing cutoff", () => {
  const offenders = collectSources(FUNCTIONS_SRC).filter(({source}) =>
    /stopBilling|@google-cloud\/billing|development-billing-cutoff|carapp-development-billing-alerts/.test(source),
  );

  assert.deepEqual(offenders.map(({name}) => name), []);
});

test("firebase.json deploys the billing cutoff only from the development codebase", () => {
  const firebaseConfig = JSON.parse(readFileSync(new URL("firebase.json", REPO_ROOT), "utf8"));

  assert.ok(Array.isArray(firebaseConfig.functions), "functions must declare one entry per codebase");
  assert.deepEqual(
    firebaseConfig.functions.map(({source, codebase}) => ({source, codebase})),
    [
      {source: "functions", codebase: "default"},
      {source: "functions-dev-billing", codebase: "dev-billing"},
    ],
  );
});

test("the product package does not depend on the Cloud Billing client", () => {
  const functionsPackage = JSON.parse(readFileSync(new URL("../package.json", import.meta.url), "utf8"));

  assert.equal("@google-cloud/billing" in (functionsPackage.dependencies ?? {}), false);
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
        entries.push({name: relative, source: readFileSync(new URL(relative, rootUrl), "utf8")});
      }
    }
  };
  visit("");
  return entries;
}
