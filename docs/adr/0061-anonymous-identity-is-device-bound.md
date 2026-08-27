# ADR-0061 / D-60 - Anonymous Identity Is Device-Bound

## Status

Accepted

## Context

Firebase Anonymous Authentication creates a temporary account without a portable sign-in
credential. The product keeps the permanent-provider sign-in option visible while an anonymous
session is active, but it must not imply that an unlinked anonymous account can be recovered on a
different device.

Firebase Authentication with Identity Platform offers native automatic cleanup for anonymous
accounts that are more than 30 days old. The retention period is fixed by Firebase. Linked
accounts are excluded from that cleanup.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Treat anonymous identity as device-bound and enable native 30-day cleanup | Matches Firebase semantics, avoids false recovery promises and uses the provider-owned lifecycle. | An inactive unlinked user can lose remote data after 30 days. |
| Promise cross-device anonymous recovery | Preserves the original walking-skeleton wording. | Firebase exposes no supported portable anonymous credential, so the promise cannot be implemented safely. |
| Keep anonymous accounts indefinitely | Avoids automatic expiry. | Accumulates abandoned identities and contradicts the selected retention policy. |

## Decision

An anonymous identity is recoverable only on the device that retains its Firebase Auth session.
It becomes recoverable on other devices only after a permanent Google or Apple credential is
linked while preserving the UID.

The development and production Firebase projects use Authentication with Identity Platform and
enable Firebase's native anonymous-account cleanup. The fixed 30-day eligibility threshold is
accepted without a custom scheduler. Existing anonymous accounts become eligible 30 days after
cleanup is enabled. Linked accounts are excluded.

## Consequences

- E0-07 proves real anonymous authentication and the Vehicle local/remote path, but no longer
  claims anonymous cross-device recovery.
- Product copy and reminders must state that an unlinked anonymous user can lose data after 30
  inactive days.
- Cross-device recovery evidence moves to E3-12, where permanent authentication and sync coexist.
- Associated Firestore data is cleaned through D-63 rather than by the Auth setting alone.

## Verification

- E0-07 contains no anonymous cross-device recovery assertion.
- E2-02 proves linking preserves the UID.
- E2-07 tests the fixed reminder schedule before the cleanup threshold.
- E3-12 proves Android-to-iOS recovery with the same permanent provider.

## References

- `docs/CONTRACTS.md §11.2`
- `docs/BACKLOG.md` (`E0-07`, `E2-02`, `E2-07`, `E3-12`)
- Firebase Anonymous Authentication documentation

