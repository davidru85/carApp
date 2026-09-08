export interface AuthUserProviderSnapshot {
    disabled?: boolean;
    providerData?: ReadonlyArray<unknown>;
}

export function isAnonymousAuthUser(user: AuthUserProviderSnapshot): boolean {
    return (user.providerData ?? []).length === 0;
}

/**
 * The `D-148` issuance predicate: a record may mint a cleanup ticket only while it still exists, is
 * known to be enabled and is still anonymous under the single shared `D-134` definition. The
 * enabled state is required explicitly (`disabled === false`), because eligibility is a positive
 * fact: a snapshot whose `disabled` value is unavailable is not known to be enabled, and disabling
 * is how an account is taken out of service without deleting it, which a stale ID token outlives.
 * The predicate fails closed on any unknown state.
 */
export function canIssueOrphanCleanupTicket(user: AuthUserProviderSnapshot | null): boolean {
    return user !== null && user.disabled === false && isAnonymousAuthUser(user);
}
