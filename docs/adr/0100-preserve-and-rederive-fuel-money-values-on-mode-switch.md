# ADR-0100 / D-99 - Preserve and Re-derive Fuel Money Values on Mode Switch

## Status

Accepted

Selected by the owner during the E1-08 review on 2026-09-01.

## Context

The Fuel Entry form supports three money input modes. Each mode treats two values as inputs and
derives the third through the D-93 resolver. The prior `CONTRACTS.md §20.10` text required a mode
switch to clear the value outside the newly selected pair, but the implemented holder preserves
the available values and immediately resolves the new derived value. Clearing discards useful
context and prevents a value derived in one mode from participating in the next derivation.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Preserve available values and immediately re-derive the value outside the selected mode | Retains user context, gives immediate feedback and reuses the single D-93 arithmetic path. | Mode changes can visibly re-round a value because the three fields use different scales. |
| Clear the value outside the selected mode | Makes the distinction between inputs and output visually strict. | Discards a valid value on every switch and forces unnecessary re-entry. |
| Keep an independent draft for each mode | Avoids cross-mode rounding and preserves every typed variant. | Introduces hidden competing values, additional reconciliation rules and presentation state beyond the MVP need. |

## Decision

`FuelEntryFormStateHolder.setMoneyInputMode(mode)` preserves all available money values. It then
uses the D-93 resolver to re-derive the value that does not participate in the newly selected mode
from the two participating values. A value previously derived may therefore become an input of the
next derivation. When both participating values are present, the mode switch clears no money value.

## Consequences

### Positive

- Users retain the values already displayed when changing how they enter money data.
- Mode switches and ordinary typing share the same D-93 resolver, formulas and range checks.
- The resulting values are visible before persistence and remain editable.

### Negative

- The scales differ: `litersScaled` and `pricePerLiterScaled` use ×1000, while
  `totalCostMinor` uses ×100. A mode toggle can therefore re-round the re-derived value, and
  repeatedly alternating modes MAY shift a value within one unit of its scale.
- This trade-off is accepted for the MVP because the change is displayed before the user saves
  and the user can correct it.

### Constraints Introduced

- Every mode-switch derivation MUST use the same D-93 resolver as validation and live typing.
- When the selected mode's participating pair is present, no money value may be cleared.
- Tests MUST pin both value preservation and the accepted one-scale-unit round-trip drift.
- This decision MUST NOT change the Swift-facing ABI.

## Verification

- Shared holder tests prove that `40001` liters-scaled units and `1549` price-scaled units derive
  `6196` total minor units, then switching to `PRICE_AND_TOTAL` re-derives liters as `40000`.
- Shared holder tests prove that a switch with both participating values present clears no value.
- The generated Objective-C header remains byte-identical to its golden.

## References

- ADR-0094 / D-93
- `docs/CONTRACTS.md §2`, `§20.10`
- `docs/BACKLOG.md` E1-08
