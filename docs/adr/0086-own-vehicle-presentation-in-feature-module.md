# ADR-0086 / D-85 - Own Vehicle Presentation in the Feature Module

## Status

Accepted

Accepted by the owner on 2026-08-30.

## Context

D-8 requires shared KMP state holders, and the architecture contract places feature presentation
code in each feature's `presentation` package. The E0-07 walking skeleton staged every exported
state holder in `:shared`, before feature implementations existed. E1-07 is the first story that
turns a staged presentation shell into production behavior.

Vehicle presentation needs shared message and sync-status types. Keeping those types in
`:shared` would reverse the dependency direction, while allowing `:feature:vehicle` presentation
to depend on `:core:sync` would weaken the D-28 package boundary.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Move Vehicle presentation to `:feature:vehicle` and shared UI primitives to `:core:common` | Matches the feature boundary; keeps presentation independent of `data` and `:core:sync`; lets D-28 guard real code. | Moves exported declarations across KMP modules and requires explicit Objective-C names. |
| Add a new `:core:presentation` module | Gives UI primitives a dedicated home. | Expands the canonical module inventory and adds a module for two small cross-feature types. |
| Keep all presentation types in `:shared` | Avoids immediate ABI movement. | Contradicts the feature-package contract and leaves D-28 guarding only scaffolding. |

## Decision

Move `VehicleListStateHolder`, `VehicleFormStateHolder`, `VehicleListUiState`,
`VehicleListItemUi` and `VehicleFormUiState` to
`com.ruizurraca.carapp.feature.vehicle.presentation` in `:feature:vehicle` commonMain. Move
`UiMessage`, `UiMessageKind` and `SyncStatus` to `:core:common`. `SyncController` remains in
`:core:sync`. Keep Fuel, Session and Sync presentation shells in `:shared` until their owning
stories. Preserve every documented Swift name with exact Objective-C naming annotations and
export the feature and common modules through the sole `Shared` framework.

## Consequences

### Positive

- Production Vehicle presentation obeys the same boundary documented for future features.
- The presentation Konsist rule remains strict and now scans real product code.
- Shared UI primitives do not create a feature-to-shared dependency.

### Negative

- The iOS composition module must explicitly export additional modules.
- Kotlin/Native naming annotations and the golden header become part of the move.

### Constraints Introduced

- Vehicle presentation MUST NOT depend on `:core:sync` or feature `data`.
- The documented Swift names MUST remain byte-exact in the generated header.
- E1-08 and E2-05 MUST move their staged feature presentation types into their owning modules.
- `SyncStateHolder` remains the app-level exception in `:shared`.

## Verification

- D-28 package rules scan the moved production declarations and retain one firing fixture per layer.
- The Objective-C golden-header diff stays empty after regeneration.
- Provider-free graph tests compile on Android host and Kotlin/Native.

## References

- ADR-0009 / D-8
- ADR-0029 / D-28
- ADR-0056 / D-55
- `docs/CONTRACTS.md §14`, `§15.3`, `§20.7`, `§20.10`
