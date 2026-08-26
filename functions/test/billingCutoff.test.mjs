import assert from "node:assert/strict";
import test from "node:test";

import {handleBudgetNotification} from "../lib/billing/handleBudgetNotification.js";

const PROJECT_ID = "davidruiz-carapp-dev";

function notification(costAmount, budgetAmount) {
  return {
    budgetAmount,
    budgetAmountType: "SPECIFIED_AMOUNT",
    budgetDisplayName: "carapp-development-monthly-budget",
    costAmount,
    currencyCode: "EUR",
  };
}

function billingGateway({enabled = true} = {}) {
  const calls = [];
  return {
    calls,
    gateway: {
      async disableBilling(projectName) {
        calls.push(["disableBilling", projectName]);
      },
      async isBillingEnabled(projectName) {
        calls.push(["isBillingEnabled", projectName]);
        return enabled;
      },
    },
  };
}

test("ignores actual cost below the budget", async () => {
  const {calls, gateway} = billingGateway();

  const result = await handleBudgetNotification({
    gateway,
    payload: notification(9.99, 10),
    projectId: PROJECT_ID,
  });

  assert.deepEqual(result, {status: "BELOW_BUDGET"});
  assert.deepEqual(calls, []);
});

test("disables billing when actual cost equals the budget", async () => {
  const {calls, gateway} = billingGateway();

  const result = await handleBudgetNotification({
    gateway,
    payload: notification(10, 10),
    projectId: PROJECT_ID,
  });

  assert.deepEqual(result, {status: "BILLING_DISABLED"});
  assert.deepEqual(calls, [
    ["isBillingEnabled", "projects/davidruiz-carapp-dev"],
    ["disableBilling", "projects/davidruiz-carapp-dev"],
  ]);
});

test("disables billing when actual cost exceeds the budget", async () => {
  const {calls, gateway} = billingGateway();

  const result = await handleBudgetNotification({
    gateway,
    payload: notification(12, 10),
    projectId: PROJECT_ID,
  });

  assert.deepEqual(result, {status: "BILLING_DISABLED"});
  assert.equal(calls.at(-1)?.[0], "disableBilling");
});

test("does not disable billing twice", async () => {
  const {calls, gateway} = billingGateway({enabled: false});

  const result = await handleBudgetNotification({
    gateway,
    payload: notification(10, 10),
    projectId: PROJECT_ID,
  });

  assert.deepEqual(result, {status: "ALREADY_DISABLED"});
  assert.deepEqual(calls, [
    ["isBillingEnabled", "projects/davidruiz-carapp-dev"],
  ]);
});

for (const payload of [
  {},
  notification("10", 10),
  notification(Number.NaN, 10),
  notification(10, 0),
  notification(-1, 10),
]) {
  test(`ignores an invalid billing payload: ${JSON.stringify(payload)}`, async () => {
    const {calls, gateway} = billingGateway();

    const result = await handleBudgetNotification({
      gateway,
      payload,
      projectId: PROJECT_ID,
    });

    assert.deepEqual(result, {status: "INVALID_NOTIFICATION"});
    assert.deepEqual(calls, []);
  });
}
