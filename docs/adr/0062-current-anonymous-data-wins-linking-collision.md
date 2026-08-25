# ADR-0062 / D-61 - Current Anonymous Data Wins a Linking Collision

## Status

Accepted

## Context

Linking a Google or Apple credential can fail with `credential-already-in-use` when that
credential already belongs to a permanent Firebase account. The product must choose which data
set survives before abandoning the current anonymous session.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Keep the current anonymous-session data and overwrite the existing permanent-account data | Preserves the data the user most recently created on the current device. | Destructive to the pre-existing permanent account and requires explicit confirmation and resumability. |
| Keep the existing permanent-account data | Preserves older recoverable data. | Discards the current device's anonymous-session data. |
| Automatically merge both data sets | Retains the most information. | Introduces conflict semantics and automatic account merging that are outside the MVP. |

## Decision

The current anonymous-session data wins. The app must show an explicit destructive confirmation
that the existing permanent-account data will be replaced. Cancellation leaves the anonymous
session and all local data unchanged.

After confirmation, the client persists a complete local snapshot and captures a fresh anonymous
ID token before switching sessions. It signs in with the colliding provider credential, replaces
the permanent account's remote data with the snapshot and can resume after interruption without
data loss. It then calls the E3-11 backend operation, which verifies ownership of the abandoned
anonymous identity and deletes it.

## Consequences

- `CredentialAlreadyInUse` is a recoverable collision state rather than `UidWouldChange`.
- The collision flow is deliberately destructive and is always confirmation-gated.
- Snapshot persistence and remote replacement must be idempotent and resumable.
- Automatic merging remains forbidden.

## Verification

- E2-04 tests cancellation, confirmation, interruption after session switch and idempotent retry.
- E3-11 tests ownership verification and deletion of the abandoned anonymous identity.
- The permanent remote account contains only the confirmed current-session snapshot afterward.

## References

- `docs/CONTRACTS.md §11.3`
- `docs/BACKLOG.md` (`E2-04`, `E3-11`)
- `D-23`, `D-63`

