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

`SESSION_CLEANUP` means the `D-23` deletion succeeded and the provider session is still alive;
`LOCAL_CLEAR` means the remote side is done and only the local clear remains. `retryDeparture()`
repeats exactly that, re-checking neither owner nor session.

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
