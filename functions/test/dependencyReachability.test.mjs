import assert from "node:assert/strict";
import Module from "node:module";
import test from "node:test";

const PROJECT_ID = "davidruiz-carapp-dev";
const AFFECTED_RUNTIME_PATHS = [
  "/node_modules/@google-cloud/storage/",
  "/node_modules/uuid/",
];

test("the deployed billing cutoff does not reach the vulnerable dependency path", async () => {
  const loadedFiles = [];
  const billingCalls = [];
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
    const {CloudBillingGateway} = await import(
      `../lib/billing/cloudBillingGateway.js?reachability=${Date.now()}`
    );
    const exportedFunctions = await import(`../lib/index.js?reachability=${Date.now()}`);
    const {createStopBilling} = await import(
      `../lib/billing/stopBilling.js?reachability=${Date.now()}`
    );
    const billingClient = {
      async getProjectBillingInfo({name}) {
        billingCalls.push(["getProjectBillingInfo", {name}]);
        return [{billingEnabled: true}];
      },
      async updateProjectBillingInfo(requestBody) {
        billingCalls.push(["updateProjectBillingInfo", requestBody]);
        return [{}];
      },
    };
    const stopBilling = createStopBilling(() => new CloudBillingGateway(billingClient));
    await stopBilling(pubsubEvent({budgetAmount: 10, costAmount: 10}));
    assert.deepEqual(Object.keys(exportedFunctions), ["stopBilling"]);
    assert.equal(
      exportedFunctions.stopBilling.__endpoint.serviceAccountEmail,
      "development-billing-cutoff@davidruiz-carapp-dev.iam.gserviceaccount.com",
    );
    assert.equal(exportedFunctions.stopBilling.__endpoint.eventTrigger.retry, false);
  } finally {
    Module._load = originalLoad;
    if (previousProjectId === undefined) {
      delete process.env.GCLOUD_PROJECT;
    } else {
      process.env.GCLOUD_PROJECT = previousProjectId;
    }
  }

  assert.deepEqual(billingCalls, [
    ["getProjectBillingInfo", {name: `projects/${PROJECT_ID}`}],
    [
      "updateProjectBillingInfo",
      {
        name: `projects/${PROJECT_ID}`,
        projectBillingInfo: {billingAccountName: ""},
      },
    ],
  ]);

  const reachedAffectedPaths = loadedFiles.filter((filename) =>
    AFFECTED_RUNTIME_PATHS.some((path) => filename.includes(path)),
  );
  assert.deepEqual(reachedAffectedPaths, []);
});

function pubsubEvent(payload) {
  return {
    data: {
      message: {
        attributes: {},
        data: Buffer.from(JSON.stringify(payload)).toString("base64"),
        messageId: "reachability-check",
        publishTime: "2026-08-26T00:00:00.000Z",
      },
      subscription: "projects/davidruiz-carapp-dev/subscriptions/test",
    },
    id: "reachability-check",
    source: "//pubsub.googleapis.com/projects/davidruiz-carapp-dev/topics/test",
    specversion: "1.0",
    time: "2026-08-26T00:00:00.000Z",
    type: "google.cloud.pubsub.topic.v1.messagePublished",
  };
}
