# ADR-0020 / D-20 - Native Resources for Localization, No Text in `UiState`

## Status

Accepted

Accepted by the owner on 2026-08-17.

## Context

The app ships in Spanish and English from day one, and the specification forbids hardcoded user-facing strings. At the same time, presentation logic is shared in `commonMain`, so a naive implementation would build user-facing strings in shared code — which has no resource bundle, and would force a shared resource library into the stack.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Native resources, with `UiState` carrying typed values only | No extra dependency; each platform localizes idiomatically, including plurals and Dynamic Type. | Each platform maintains its own string catalogue and must cover every typed value. |
| Shared resource library (for example moko-resources) | One string catalogue. | Extra dependency and code generation, plus weaker platform idioms, for a UI that is native by decision `D-7` and `D-8`. |
| Strings built in shared code | Single formatting path. | Requires a shared bundle anyway and breaks the "no business logic in UI, no UI text in shared logic" separation. |

## Decision

Localize with native Android and iOS resources. `UiState` MUST NOT contain user-facing text: it carries typed values — `AppError` leaves, `ConsumptionInvalidReason`, enum states — and raw scaled numbers and instants. Each platform maps those to its own strings and formats numbers and dates locally.

## Consequences

### Positive

- No shared resource dependency, and no risk of shared code emitting an untranslated string.
- Plurals, Dynamic Type and locale-aware number formatting behave natively on each platform.

### Negative

- Two string catalogues must stay in sync, and a new typed value requires a new string on both platforms.

### Constraints Introduced

- Adding a leaf to `AppError` or `ConsumptionInvalidReason` REQUIRES adding its Spanish and English strings on both platforms in the same change.
- `E4-02` verifies that every typed value has a mapping and that no user-facing text remains in `UiState`.

## Verification

- Architecture or contract check asserts `UiState` types contain no `String` fields intended for display.
- `E4-02` acceptance criteria cover completeness of both catalogues.

## References

- `docs/DECISION_BOARD.md` (`D-20`)
- `docs/CONTRACTS.md` §14
- `docs/SPECIFICATION.md` §11
