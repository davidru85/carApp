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

export async function handleBudgetNotification(
    _input: HandleBudgetNotificationInput,
): Promise<{status: BillingCutoffStatus}> {
    throw new Error("Not implemented");
}
