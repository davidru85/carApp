# ADR-0096 / D-95 - Stage Fuel Sync Status Until E3-03

## Status

Accepted

Accepted by the owner for E1-08 on 2026-08-31.

## Context

`FuelEntryListUiState` exposes `SyncStatus`, but E3-03 owns the only final `SyncController` and has
not started. D-88 authorises constant `Idle` only for Vehicle presentation, so extending the same
temporary behavior to Fuel presentation requires an explicit scope decision.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Publish constant `Idle` from the Fuel Entry list until E3-03 | Avoids a second sync authority and keeps E1-08 in Phase 1 scope. | Fuel backup progress is not visible yet. |
| Build a provisional Fuel controller | Gives the field a dynamic source now. | Creates disposable behavior and a competing sync authority. |
| Pull E3-03 forward | Delivers the final invariant immediately. | Absorbs the complete gated sync engine into a UI story. |

## Decision

Fuel Entry list presentation publishes constant `SyncStatus.Idle` until E3-03. It does not create
or call a provisional controller. E3-03 closes both D-88 and D-95 by wiring every exposing holder
to the single `SyncController.status` and proving convergence.

## Consequences

### Positive

- One final synchronization authority remains the architectural invariant.
- E1-08 does not implement any part of E3-03.

### Negative

- Fuel Entry backup progress is temporarily absent from the list.

### Constraints Introduced

- `FuelEntryListUiState.syncStatus` is constant `Idle` until E3-03.
- E3-03 MUST remove this exception and retain the existing two-holder convergence criterion.

## Verification

- Fuel Entry state-holder tests assert the staged constant status.
- No provisional controller construction or access is added.

## References

- ADR-0089 / D-88
- `docs/CONTRACTS.md §14`
- `docs/BACKLOG.md` E3-03
