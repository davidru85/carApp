export interface AuthUserProviderSnapshot {
    disabled?: boolean;
    providerData?: ReadonlyArray<unknown>;
}

export function isAnonymousAuthUser(user: AuthUserProviderSnapshot): boolean {
    return (user.providerData ?? []).length === 0;
}

/**
 * The `D-148` issuance predicate: a record may mint a cleanup ticket only while it still exists, is
 * enabled and is still anonymous under the single shared `D-134` definition. A disabled record is
 * rejected because disabling is how an account is taken out of service without deleting it, and a
 * stale ID token outlives that change.
 */
export function canIssueOrphanCleanupTicket(user: AuthUserProviderSnapshot | null): boolean {
    return user !== null && user.disabled !== true && isAnonymousAuthUser(user);
}
