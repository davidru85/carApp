export interface AuthUserProviderSnapshot {
    providerData?: ReadonlyArray<unknown>;
}

export function isAnonymousAuthUser(user: AuthUserProviderSnapshot): boolean {
    return (user.providerData ?? []).length === 0;
}
