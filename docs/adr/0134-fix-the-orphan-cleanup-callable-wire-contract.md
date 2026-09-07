# ADR-0134 / D-133 - Fix the Orphan-Cleanup Callable Wire Contract

## Status

Accepted

## Context

The account-linking collision flow (`docs/CONTRACTS.md §11.3`) ends by asking the backend to
delete the abandoned anonymous identity. D-128 fixed the `deleteAccount` wire contract, but the
`deleteOrphanedAnonymousAccount` callable added by E3-11 needs its own closed contract: the
payload field, the success literal and the error-code set that E2-04 must map to typed
`AuthError` values.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Fix the implemented contract: `anonymousIdToken`, success `{status: "ORPHANED_ANONYMOUS_ACCOUNT_DELETED"}`, errors `unauthenticated` / `invalid-argument` / `failed-precondition` / `internal` | Matches the deployed handler covered by the E3-11 suite; `failed-precondition` distinguishes a non-anonymous captured identity and the current-permanent-UID guard exactly as §11.3 requires; error codes are closed and typed for the client. | Introduces a second deletion status literal alongside D-128. |
| Reuse the D-128 surface: `{status: "ACCOUNT_DELETED"}` and only `unauthenticated` / `invalid-argument` / `internal` | One deletion contract to maintain. | Conflates two distinct operations; loses the typed distinction between an invalid token, a non-anonymous captured account and the permanent-UID guard, all of which the collision flow needs to report or resume from. |
| Add `permission-denied` for the permanent-UID guard | Reuses a code the client already maps from D-128. | §11.3 frames the guard as an input precondition, not an authorization failure; a second precondition code adds surface the E2-04 flow does not need. |

## Decision

`deleteOrphanedAnonymousAccount` is a 2nd gen callable whose request payload contains
`anonymousIdToken: String`. A successful response is `{ status:
"ORPHANED_ANONYMOUS_ACCOUNT_DELETED" }`. Missing authentication maps to `unauthenticated`, a
missing or invalid `anonymousIdToken` maps to `invalid-argument`, a captured identity that is not
an anonymous account or that equals the current permanent UID maps to `failed-precondition`, and
an Admin Auth or remote-data deletion failure maps to `internal`. No UID, token, request payload
or raw provider failure is attached to callable logs.

## Consequences

### Positive

- The client has a stable, typed wire contract before E2-04 exists.
- The permanent-UID guard and the anonymous-eligibility guard are distinguishable from transport
  and validation failures.
- Log redaction follows the same closed-context rule as D-128.

### Negative

- Two deletion callables exist with different success literals; the client must map each one.

### Constraints Introduced

- Any change to the payload field, the success literal or the error-code set requires a
  superseding owner decision and an atomic update of `docs/CONTRACTS.md §11.5`.
- `docs/CONTRACTS.md §11.5` records this exact contract; no other spelling is valid.

## Verification

- `functions/test/orphanedAnonymousAccount.test.mjs` pins every error-code mapping, the success
  literal and log redaction.
- `functions/test/functionGenerationPolicy.test.mjs` proves the callable is 2nd gen and exported
  under its fixed name.

## References

- `docs/CONTRACTS.md §11.3`, `§11.5`
- `docs/adr/0129-fix-the-account-deletion-callable-contract.md` (`D-128`)
- `docs/BACKLOG.md` (`E2-04`, `E3-11`)
