import {logger} from "firebase-functions";
import {onMessagePublished} from "firebase-functions/v2/pubsub";

import {CloudBillingGateway} from "./cloudBillingGateway.js";
import {
    type BillingGateway,
    handleBudgetNotification,
} from "./handleBudgetNotification.js";

const BILLING_BUDGET_TOPIC = "carapp-development-billing-alerts";

export function createStopBilling(
    gatewayFactory: () => BillingGateway = () => new CloudBillingGateway(),
) {
    return onMessagePublished(
        {
            concurrency: 1,
            maxInstances: 1,
            memory: "256MiB",
            region: "europe-west1",
            retry: false,
            serviceAccount: "development-billing-cutoff@davidruiz-carapp-dev.iam.gserviceaccount.com",
            timeoutSeconds: 60,
            topic: BILLING_BUDGET_TOPIC,
        },
        async (event) => {
            const projectId = process.env.GCLOUD_PROJECT ?? process.env.GOOGLE_CLOUD_PROJECT;
            if (projectId === undefined || projectId.length === 0) {
                throw new Error("Cloud project ID is unavailable");
            }

            const result = await handleBudgetNotification({
                gateway: gatewayFactory(),
                payload: event.data.message.json,
                projectId,
            });
            logger.info("Development billing cutoff evaluated", {status: result.status});
        },
    );
}
