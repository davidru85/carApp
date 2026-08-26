export type BillingCutoffStatus =
    | "ALREADY_DISABLED"
    | "BELOW_BUDGET"
    | "BILLING_DISABLED"
    | "INVALID_NOTIFICATION";

export interface BillingGateway {
    disableBilling(projectName: string): Promise<void>;
    isBillingEnabled(projectName: string): Promise<boolean>;
}

export interface HandleBudgetNotificationInput {
    gateway: BillingGateway;
    payload: unknown;
    projectId: string;
}

interface BudgetNotification {
    budgetAmount: number;
    costAmount: number;
}

export async function handleBudgetNotification(
    input: HandleBudgetNotificationInput,
): Promise<{status: BillingCutoffStatus}> {
    if (!isBudgetNotification(input.payload)) {
        return {status: "INVALID_NOTIFICATION"};
    }

    if (input.payload.costAmount < input.payload.budgetAmount) {
        return {status: "BELOW_BUDGET"};
    }

    const projectName = `projects/${input.projectId}`;
    if (!(await input.gateway.isBillingEnabled(projectName))) {
        return {status: "ALREADY_DISABLED"};
    }

    await input.gateway.disableBilling(projectName);
    return {status: "BILLING_DISABLED"};
}

function isBudgetNotification(payload: unknown): payload is BudgetNotification {
    if (payload === null || typeof payload !== "object") {
        return false;
    }

    const candidate = payload as Record<string, unknown>;
    return isNonNegativeFiniteNumber(candidate.costAmount) &&
        isPositiveFiniteNumber(candidate.budgetAmount);
}

function isNonNegativeFiniteNumber(value: unknown): value is number {
    return typeof value === "number" && Number.isFinite(value) && value >= 0;
}

function isPositiveFiniteNumber(value: unknown): value is number {
    return typeof value === "number" && Number.isFinite(value) && value > 0;
}
