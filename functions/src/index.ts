import {createStopBilling} from "./billing/stopBilling.js";
export {onAnonymousUserDeleted} from "./auth/onAnonymousUserDeleted.js";
export {deleteAccount} from "./callable/deleteAccount.js";
export {deleteOrphanedAnonymousAccount} from "./callable/deleteOrphanedAnonymousAccount.js";

export const stopBilling = createStopBilling();
