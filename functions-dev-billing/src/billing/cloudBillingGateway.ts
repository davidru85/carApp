import {CloudBillingClient} from "@google-cloud/billing";

import type {BillingGateway} from "./handleBudgetNotification.js";

export class CloudBillingGateway implements BillingGateway {
    public constructor(
        private readonly client: Pick<
            CloudBillingClient,
            "getProjectBillingInfo" | "updateProjectBillingInfo"
        > = new CloudBillingClient(),
    ) {}

    public async disableBilling(projectName: string): Promise<void> {
        await this.client.updateProjectBillingInfo({
            name: projectName,
            projectBillingInfo: {billingAccountName: ""},
        });
    }

    public async isBillingEnabled(projectName: string): Promise<boolean> {
        const [billingInfo] = await this.client.getProjectBillingInfo({
            name: projectName,
        });
        return billingInfo.billingEnabled === true;
    }
}
