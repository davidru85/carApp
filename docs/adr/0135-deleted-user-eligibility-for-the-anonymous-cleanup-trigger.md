# ADR-0135 / D-134 - Deleted User Eligibility for the Anonymous Cleanup Trigger

## Status

Accepted

## Context

`onAnonymousUserDeleted` reacts to Firebase Auth user deletions. `docs/CONTRACTS.md §11.5` states
that only an *eligible deleted anonymous UID* is delegated to the D-63 service, but the predicate
was not executable. The trigger runs with Admin privileges, so a wrong predicate would delete
remote data of permanently linked users who delete their accounts, which §11.5 and D-23 forbid.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Eligible = deleted user with an empty `providerData` list | Matches the Firebase representation of an unlinked anonymous account: linking any permanent provider adds a provider entry, and Phone- or Google-only accounts carry one; the predicate is a closed, documented Firebase property already exercised in the E3-11 suite. | Couples the guard to the current Firebase representation; a provider-model change would require a review of this predicate. |
| Eligible = empty `providerData` or password-only accounts | Would also cover email/password accounts if they were ever enabled. | Contradicts D-63 and `docs/SPECIFICATION.md §11.2`: linking a permanent provider removes an account from anonymous cleanup, and password is a permanent provider; it also exceeds the MVP surface. |
| No eligibility filter; rely on service idempotency | Smallest handler. | The trigger would invoke the deletion service for permanently linked users on their own account deletion, violating §11.5 and D-23. |

## Decision

A deleted Auth user is eligible when `providerData` is empty. A deleted user with any provider
entry, including a phone-only user, is skipped with a redacted `NOT_ANONYMOUS` log, and a user
record without a UID is skipped with a `MISSING_UID` log. Trigger delivery caused by a
non-automatic anonymous deletion path is treated as harmless idempotent overlap.

## Consequences

### Positive

- The predicate is a single documented Firebase property with a closed outcome.
- Linked, phone and password identities cannot be affected by the automatic-cleanup trigger.
- Overlap between the trigger and the `deleteOrphanedAnonymousAccount` Admin path remains
  provably harmless.

### Negative

- The predicate inherits any future Firebase provider-representation change and must be reviewed
  in the TD-01 migration.

### Constraints Introduced

- `onAnonymousUserDeleted` MUST NOT delegate a UID whose deleted record carries any
  `providerData` entry.
- A UID-less record MUST be skipped without deletion and without throwing.

## Verification

- `functions/test/anonymousCleanup.test.mjs` covers anonymous, linked, phone-only, UID-less,
  redelivery, retry-after-failure, concurrent-overlap and log-redaction cases.
- The TD-01 migration surface in `docs/TECHNICAL_PLAN.md §13` reuses the same assertions against
  the eventual 2nd gen trigger.

## References

- `docs/CONTRACTS.md §11.5`
- `docs/adr/0064-own-user-data-cleanup-service.md` (`D-63`)
- [Firebase Authentication trigger documentation](https://firebase.google.com/docs/functions/1st-gen/auth-events)
