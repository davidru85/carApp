# ADR-0112 / D-111 - Explicit UID Change Opt-In for Account Adoption

## Status

Accepted

Selected during the E2-02 owner review remediation on 2026-09-03.

## Context

`docs/CONTRACTS.md §11.3` defines the Account Adoption flow: when linking fails because a permanent credential is already associated with an existing account (Step 2: Collision), the application offers the user the choice to adopt that existing account. Adopting the existing account authenticates directly into that permanent account, replacing the anonymous session and its UID.

However, `AuthClient.signInWithCredential` previously had no parameter to distinguish between an intentional account adoption and an accidental identity change during an anonymous session. Under the defensive check, `FirebaseAuthClient` rejected all sign-ins that would change the UID of an active anonymous session with `AuthError.UidWouldChange`, effectively making the documented Account Adoption step 2 impossible to execute via `signInWithCredential`.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Add optional parameter `allowUidChange: Boolean = false` to `AuthClient.signInWithCredential` (Selected) | Preserves source compatibility for callers expecting safety by default; callers performing intentional account adoption explicitly opt in with `allowUidChange = true`; minimal API surface change; fully documented in contracts | Adds a boolean parameter to `AuthClient.signInWithCredential` across interface and implementations |
| Always allow UID change unconditionally | Simplifies method signature; avoids adding a parameter | Loses defensive protection against accidental session replacement when linking was intended; violates principle of least surprise for callers |
| Create a separate `adoptAccountWithCredential` API on `AuthClient` | Explicit named intent in the method name | Expands the contract interface surface with redundant method doing identical underlying authentication; requires duplicate wiring and fakes |

## Decision

Add `allowUidChange: Boolean = false` to `AuthClient.signInWithCredential(credential: NativeAuthCredential, allowUidChange: Boolean = false)`. By default (`allowUidChange = false`), signing in with a credential when an anonymous session is active and would change the UID continues to be rejected with `AuthError.UidWouldChange`. When `allowUidChange = true` is passed, the client permits the identity switch, signs in with the credential, and updates `authState` to the permanent account session.

## Consequences

- `AuthClient` contract interface in `:core:auth` and its test/fake implementations accept `allowUidChange: Boolean = false`.
- `FirebaseAuthClient` enforces `allowUidChange`: if `gateway.currentUser?.isAnonymous == true && !allowUidChange`, it returns `AuthError.UidWouldChange`. If `allowUidChange == true`, it proceeds with sign-in and updates `authState`.
- `docs/CONTRACTS.md §11.1`, `§11.3`, and `§20.8` document this parameter and its interaction with Account Adoption.
- Parity across `docs/DECISION_BOARD.md`, `docs/SPECIFICATION.md §12`, `docs/TECHNICAL_PLAN.md §2`, and `docs/adr/README.md` is maintained and verified by `contractCheck`.
