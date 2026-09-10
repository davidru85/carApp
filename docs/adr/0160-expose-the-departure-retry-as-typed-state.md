# ADR-0160 / D-159 - Expose the Departure Retry as Typed State

## Status

Accepted

## Context

A departure retains its request when a step fails after an earlier one succeeded, so that a retry
repeats only the unfinished part. That retention was unreachable from the public contract:
`confirmSignOut()` requires a pending-sync warning that an empty outbox never produces, and
`requestSignOut()` has no signed-in session left to work from once the provider sign-out succeeded.
For a local owner the phase had already moved away from `LOCAL`, so the ownership check discarded the
request instead of retrying it.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. `SessionUiState.pendingDepartureRetry: DepartureRetry?` plus `retryDeparture()` | The host reads what can be retried from typed state and calls one intent; the enum admits further modes without another ABI change | Adds a member and a function to the exported surface |
| B. A boolean plus a retry intent | The smallest possible ABI addition | A boolean cannot say what remains, so a second retry mode would need another ABI change |
| C. Relax the existing confirmation guards | No ABI change | The host would have to remember that something is pending, which the presentation contract forbids; and relaxing the `warned` guard reopens acceptance of unsolicited confirmations |

## Decision

The selected option is **A**.

The value is the first required step the departure still owes, in the order its kind performs them.
`SESSION_CLEANUP` means the provider-session cleanup is next: after a `D-23` deletion that succeeded,
after a sign-out whose provider sign-out failed, and after an anonymous local-data deletion whose
local clear succeeded. `LOCAL_CLEAR` means the local clear is next. The value is `null` when no retry
is callable, including while a stale login awaits re-authentication. `retryDeparture()` repeats
exactly the published step, re-checking neither owner nor session.

An earlier revision described `SESSION_CLEANUP` as belonging to the permanent `D-23` path alone and
derived retained work from the local clear only. That was too narrow: it left a failed sign-out and a
failed anonymous session cleanup reporting `LOCAL_CLEAR`, and the anonymous case with no retry at
all. This is a correction of the same accepted decision, not a change of scope.

## Consequences

### Positive

- The retry is discoverable from state rather than from documentation.
- The ownership check no longer discards a request whose original session is legitimately gone.

### Negative

- Two more exported declarations, and one more state field to keep clear.

### Constraints Introduced

- A retry MUST NOT repeat a `D-23` deletion that already succeeded.
- `pendingDepartureRetry` MUST be `null` whenever there is nothing to retry.

## Verification

- `aLocalClearFailureOffersATypedRetry`, `retryDepartureRepeatsOnlyTheLocalClearAfterASignOut`,
  `retryDepartureRepeatsTheLocalClearForALocalOwner`,
  `retryDepartureNeverRepeatsAServerDeletionThatSucceeded` and
  `retryDepartureDoesNothingWithoutRetainedWork`.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-159`)
- `docs/SPECIFICATION.md §7 F-5`, `§12`
- `docs/CONTRACTS.md §11.5`, `§20.2`, `§20.10`
- `docs/TECHNICAL_PLAN.md §2`
- `docs/handoff-E2-05.md`
