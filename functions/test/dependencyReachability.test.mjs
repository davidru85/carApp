import assert from "node:assert/strict";
import Module from "node:module";
import test from "node:test";

const PROJECT_ID = "davidruiz-carapp-dev";
const AFFECTED_RUNTIME_PATHS = [
  "/node_modules/@google-cloud/storage/",
  "/node_modules/uuid/",
];

test("loading the product functions does not reach the vulnerable dependency path", async () => {
  const loadedFiles = [];
  const originalLoad = Module._load;
  const previousProjectId = process.env.GCLOUD_PROJECT;

  Module._load = function trackedLoad(request, parent, isMain) {
    const loaded = originalLoad.apply(this, arguments);
    const filename = Module._resolveFilename(request, parent, isMain);
    if (typeof filename === "string") {
      loadedFiles.push(filename);
    }
    return loaded;
  };
  process.env.GCLOUD_PROJECT = PROJECT_ID;

  try {
    const exportedFunctions = await import(`../lib/index.js?reachability=${Date.now()}`);
    assert.deepEqual(Object.keys(exportedFunctions).sort(), [
      "deleteAccount",
      "deleteOrphanedAnonymousAccount",
      "issueOrphanCleanupTicket",
      "onAnonymousUserDeleted",
    ]);
    assert.equal(exportedFunctions.deleteAccount.__endpoint.platform, "gcfv2");
    assert.deepEqual(exportedFunctions.deleteAccount.__endpoint.region, ["europe-west1"]);
    assert.deepEqual(
      {
        availableMemoryMb: exportedFunctions.deleteAccount.__endpoint.availableMemoryMb,
        concurrency: exportedFunctions.deleteAccount.__endpoint.concurrency,
        maxInstances: exportedFunctions.deleteAccount.__endpoint.maxInstances,
        timeoutSeconds: exportedFunctions.deleteAccount.__endpoint.timeoutSeconds,
      },
      {
        availableMemoryMb: 256,
        concurrency: 1,
        maxInstances: 3,
        timeoutSeconds: 300,
      },
    );
    assert.equal(exportedFunctions.deleteOrphanedAnonymousAccount.__endpoint.platform, "gcfv2");
    assert.deepEqual(exportedFunctions.deleteOrphanedAnonymousAccount.__endpoint.region, ["europe-west1"]);
    assert.deepEqual(
      {
        availableMemoryMb: exportedFunctions.deleteOrphanedAnonymousAccount.__endpoint.availableMemoryMb,
        maxInstances: exportedFunctions.deleteOrphanedAnonymousAccount.__endpoint.maxInstances,
        timeoutSeconds: exportedFunctions.deleteOrphanedAnonymousAccount.__endpoint.timeoutSeconds,
      },
      {
        availableMemoryMb: 256,
        maxInstances: 2,
        timeoutSeconds: 60,
      },
    );
    assert.equal(exportedFunctions.issueOrphanCleanupTicket.__endpoint.platform, "gcfv2");
    assert.deepEqual(exportedFunctions.issueOrphanCleanupTicket.__endpoint.region, ["europe-west1"]);
    assert.deepEqual(
      {
        availableMemoryMb: exportedFunctions.issueOrphanCleanupTicket.__endpoint.availableMemoryMb,
        maxInstances: exportedFunctions.issueOrphanCleanupTicket.__endpoint.maxInstances,
        timeoutSeconds: exportedFunctions.issueOrphanCleanupTicket.__endpoint.timeoutSeconds,
      },
      {
        availableMemoryMb: 256,
        maxInstances: 2,
        timeoutSeconds: 60,
      },
    );
    assert.equal(exportedFunctions.onAnonymousUserDeleted.__endpoint.platform, "gcfv1");
    assert.deepEqual(exportedFunctions.onAnonymousUserDeleted.__endpoint.region, ["europe-west1"]);
    assert.deepEqual(
      {
        availableMemoryMb: exportedFunctions.onAnonymousUserDeleted.__endpoint.availableMemoryMb,
        maxInstances: exportedFunctions.onAnonymousUserDeleted.__endpoint.maxInstances,
        timeoutSeconds: exportedFunctions.onAnonymousUserDeleted.__endpoint.timeoutSeconds,
      },
      {
        availableMemoryMb: 256,
        maxInstances: 2,
        timeoutSeconds: 60,
      },
    );
    assert.equal(exportedFunctions.onAnonymousUserDeleted.__endpoint.eventTrigger.retry, true);
  } finally {
    Module._load = originalLoad;
    if (previousProjectId === undefined) {
      delete process.env.GCLOUD_PROJECT;
    } else {
      process.env.GCLOUD_PROJECT = previousProjectId;
    }
  }


  const reachedAffectedPaths = loadedFiles.filter((filename) =>
    AFFECTED_RUNTIME_PATHS.some((path) => filename.includes(path)),
  );
  assert.deepEqual(reachedAffectedPaths, []);
});
