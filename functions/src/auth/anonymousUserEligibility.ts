export interface AuthUserProviderSnapshot {
    disabled?: boolean;
    providerData?: ReadonlyArray<unknown>;
}

export function isAnonymousAuthUser(user: AuthUserProviderSnapshot): boolean {
    return (user.providerData ?? []).length === 0;
}

/**
 * The `D-148` issuance predicate, applied to the Admin snapshot resolved immediately before the
 * authorization write: the snapshot exists, is known to be enabled and is still anonymous under the
 * single shared `D-134` definition. The enabled state is required explicitly (`disabled === false`),
 * because eligibility is a positive fact: a snapshot whose `disabled` value is unavailable is not
 * known to be enabled, and disabling is how an account is taken out of service without deleting it,
 * which a stale ID token outlives. The predicate fails closed on any unknown state.
 *
 * Scope, stated because it is easy to overstate. This predicate decides a fact about the snapshot
 * that the lookup returned. It rejects a token whose identity was **already** linked, disabled,
 * deleted or state-unknown when the lookup resolved, and that rejection creates no authorization.
 * It does **not** prove the identity is still eligible when the Firestore write commits: Firebase
 * Auth and Firestore share no transaction, so the account can be linked, disabled or deleted in the
 * window between the lookup returning and the write committing. That window is `D-150` / `E3-16`,
 * and it is unresolved. A second Admin read, a post-write read-back, a retry or a compensating
 * delete does not close it, because none of them is atomic with both the Auth transition and the
 * Firestore write.
 */
export function canIssueOrphanCleanupTicket(user: AuthUserProviderSnapshot | null): boolean {
    return user !== null && user.disabled === false && isAnonymousAuthUser(user);
}
