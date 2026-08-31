# ADR-0095 / D-94 - Compose Fuel Entry Form Defaults in AppGraph

## Status

Accepted

Accepted by the owner for E1-08 on 2026-08-31.

## Context

F-3 requires the exact current instant, the Vehicle current odometer and the settings currency as
creation defaults. E1-10 has not yet implemented `SettingsRepository`, and the feature dependency
rules forbid `:feature:fuel` from depending on `:feature:vehicle`.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Compose primitive defaults in `AppGraph` from the Vehicle observation, `AppClock` and locale provider | Preserves feature isolation and keeps E1-10 outside the PR. | Locale-derived currency is an explicit temporary source until E1-10. |
| Add a presentation-oriented defaults query to `FuelEntryRepository` | Gives the holder one dependency. | Expands repository and database access for a composition concern and still lacks final settings. |
| Implement E1-10 before or inside E1-08 | Uses persisted settings immediately. | Violates one-story scope and combines independent TDD deliveries. |

## Decision

`AppGraph` injects the Vehicle current-odometer observation into the Fuel Entry form holder without
exposing a Vehicle type to `:feature:fuel`. The holder receives the exact `AppClock.now()` instant.
The composition point supplies `LocaleProvider.current().suggestedCurrency` when supported by
`SUPPORTED_CURRENCY_CODES` and `MinorUnits`, otherwise `EUR`. A `TODO(E1-10)` exists only at that
composition point so E1-10 can replace the temporary currency source with persisted settings.

## Consequences

### Positive

- Feature-to-feature dependencies remain forbidden and executable.
- E1-08 gets real F-3 defaults without absorbing settings persistence.

### Negative

- Until E1-10, the default currency cannot reflect a persisted user override.
- AppGraph composition coordinates one primitive Vehicle fact for the Fuel feature.

### Constraints Introduced

- `:feature:fuel` MUST NOT import `:feature:vehicle`.
- No `SettingsRepository` or settings type is added by E1-08.
- The default instant MUST remain the untouched value returned by `AppClock.now()`.

## Verification

- AppGraph and state-holder tests inject deterministic clock, locale and odometer values.
- Architecture checks continue to reject feature-to-feature dependencies.

## References

- `docs/SPECIFICATION.md §7` F-3
- `docs/CONTRACTS.md §14`, `§20.10`
- `docs/BACKLOG.md` E1-08, E1-10
