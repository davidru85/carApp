# ADR-0168 / D-167 - Resume an Interrupted Departure at Graph Construction

## Status

Accepted

## Context

The `D-166` marker records what an interrupted departure still owes. Something has to read it and
finish the job, and the choice of where determines whether the recovery runs at all and whether the
owner is asked anything.

Two facts shape it. The departure was already authorised by an explicit destructive confirmation, so
there is nothing to ask again. And the `D-23` server operation must never be repeated, nor started
without a confirmation.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. `AccountDepartureCoordinator.resumePending()` launched at app-graph construction | Mirrors the `D-151` conversion resumption the graph already launches; runs exactly once per launch regardless of which host surface appears first; needs no UI state | The recovery is invisible to the owner, so a failure is only retried at the next launch |
| B. Resume when `SessionStateHolder` is created | Reuses the state machine that wrote the marker, and could publish progress | The holder is created per host scope and may be created more than once or not at all, so the recovery is neither guaranteed nor single |
| C. Resume on the next auth-state emission | Reuses the collector the graph already runs | A local-owner departure has no auth session to emit, so its recovery would never run |
| D. Offer the resumption to the owner as a prompt | The owner sees what is happening | Asks again for something already confirmed, and leaves local data for a deleted account on the device until the owner answers |

## Decision

The selected option is **A**. The graph launches `resumePending()` once at construction. It finishes
only the steps the marker does not record as done, in the order that kind performs them, and reports
nothing to the owner.

A permanent deletion whose `D-23` call never recorded success is **dropped**, not resumed: repeating
that call is forbidden and starting it would need a confirmation nobody gave, so the marker is
removed and the owner keeps both account and data.

## Consequences

### Positive

- An interrupted departure is finished before anything can observe local data belonging to an
  account that is already gone.
- Every departure kind recovers, including a local owner's, which has no auth session at all.

### Negative

- A recovery that fails, for example a sign-out with no network, is silent and waits for the next
  launch. The marker survives, so nothing is lost.

### Constraints Introduced

- The resumption MUST NOT call `AuthClient.deleteAccount()`, under any marker state.
- It MUST NOT publish session state or a message: it is the app finishing its own job.
- It MUST clear the marker when it finishes, and leave it in place when a step fails.

## Verification

- `AccountDepartureRecoveryTest` builds a fresh coordinator over the same database, as the graph
  does, for every kind: an interrupted permanent deletion, anonymous deletion, sign-out and local
  owner deletion, plus the dropped case and the no-marker case.
- `aDepartureIsPersistedBeforeItsFirstDestructiveStepAndClearedOnSuccess` pins the write order the
  recovery depends on.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-167`)
- `docs/CONTRACTS.md §11.5`, `§20.10`
- `docs/TECHNICAL_PLAN.md §2`
- `docs/adr/0152-store-the-conversion-marker-in-normalized-tables.md`
- `docs/handoff-E2-05.md`
