# ADR-0089 / D-88 - Stage Idle Sync Status Until E3-03

## Status

Accepted

Accepted by the owner on 2026-08-30.

## Context

The final presentation contract requires every state holder that exposes `SyncStatus` to relay
the single `SyncController.status`. E3-03 owns that controller and its synchronization engine.
E1-07 still retains the narrow E0-07 direct Vehicle restoration path under D-55.

A provisional controller in E1-07 would coexist with direct restoration, create a second source
of truth and be discarded when E3-03 implements the final engine.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Build a provisional `SyncController` in E1-07 | Makes the final presentation wiring executable early. | Creates two sync authorities and disposable behavior outside the story. |
| Keep direct restoration and constant `SyncStatus.Idle` until E3-03 | Preserves D-55 scope and one real sync authority; makes the exception explicit and temporary. | Sync progress is not surfaced during the staged direct restore. |
| Pull E3-03 forward before E1-07 | Delivers the final invariant immediately. | Reorders and absorbs a large Phase 3 engine story into Phase 1 UI work. |

## Decision

E1-07 keeps the D-55 direct restoration adapter and exposes constant `SyncStatus.Idle` from
Vehicle presentation. It does not construct a provisional `SyncController`. The staged Kotlin
graph sync accessor remains unavailable until E3-03, and the staged Swift `SyncStateHolder`
remains deterministic and non-mutating.

E3-03 closes this exception by wiring every state holder that exposes `SyncStatus` to the one
`SyncController.status` flow and by testing two-holder convergence.

## Consequences

### Positive

- There is no disposable or competing synchronization controller.
- E1-07 stays within the already accepted walking-skeleton boundary.
- The final convergence requirement remains explicit and testable in its owning story.

### Negative

- The Vehicle list reports `Idle` during direct restoration in E1-07.
- The Kotlin graph sync accessor cannot be used before E3-03.

### Constraints Introduced

- E1-07 MUST NOT implement a provisional `SyncController`.
- `VehicleListUiState.syncStatus` is constant `Idle` until E3-03.
- E3-03 MUST wire every exposing holder to one controller and add the two-holder convergence test.

## Verification

- Vehicle state-holder tests assert the constant staged status.
- E3-03 acceptance criteria name the convergence wiring and test.
- The E1-07 handoff and project log retain the open exception.

## References

- ADR-0056 / D-55
- `docs/CONTRACTS.md §14`
- `docs/BACKLOG.md` E3-03
