import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import test from "node:test";

const REPO_ROOT = new URL("../../", import.meta.url);

test("the functions emulator script resolves the pinned repository-root Firebase CLI", () => {
  const functionsPackage = JSON.parse(readFileSync(
    new URL("functions/package.json", REPO_ROOT),
    "utf8",
  ));
  const rootPackage = JSON.parse(readFileSync(
    new URL("package.json", REPO_ROOT),
    "utf8",
  ));

  const emulatorScript = functionsPackage.scripts["test:emulator"];
  assert.equal(
    typeof emulatorScript,
    "string",
    "functions test:emulator script must exist",
  );

  assert.equal(
    emulatorScript.includes("npx"),
    false,
    "test:emulator must not rely on implicit npx resolution, which can fetch an unpinned firebase-tools from the network",
  );

  const rootFirebaseTools = rootPackage.devDependencies["firebase-tools"];
  assert.equal(rootFirebaseTools, "15.28.1");
  assert.match(
    emulatorScript,
    /node \.\.\/node_modules\/firebase-tools\/lib\/bin\/firebase\.js/,
    "test:emulator must invoke the repository-root pinned firebase-tools binary explicitly",
  );

  const declaredFunctions = functionsPackage.devDependencies ?? {};
  assert.equal(
    "firebase-tools" in declaredFunctions,
    false,
    "firebase-tools must not be duplicated as a functions dependency; the repository-root pin is the single source",
  );
});