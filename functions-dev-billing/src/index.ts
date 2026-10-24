import {createStopBilling} from "./billing/stopBilling.js";

// D-247: the development-only codebase. Only davidruiz-carapp-dev deploys it; production never does.
export const stopBilling = createStopBilling();
