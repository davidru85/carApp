# ADR-0094 / D-93 - Reuse Pure Money Input Resolution

## Status

Accepted

Accepted by the owner for E1-08 on 2026-08-31.

## Context

The Fuel Entry form must derive the third R-2 monetary value live. The exact resolver and range
checks already exist privately inside `FuelEntryValidation.kt`. Duplicating them in presentation
would create a second implementation, while invoking the complete validator would require
unrelated date and odometer facts.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Promote the existing resolver and canonical values to module-visible Kotlin declarations | Reuses one formula and one set of bounds from validation and presentation. | Adds internal feature API that must remain hidden from Objective-C. |
| Reimplement the formulas and bounds in the state holder | Avoids changing validation visibility. | Duplicates business rules and can diverge from save behavior. |
| Invoke the full validator with synthetic non-money facts | Reuses the current public validator. | Produces unrelated errors and couples live calculation to fabricated context. |

## Decision

Promote the existing `resolveMoney(input, minorUnitFactor)` and `CanonicalMoneyValues` declarations
to module-visible Kotlin API, keep the full validator calling that exact implementation, and hide
both declarations from Objective-C. A live-resolution error clears only the derived third value;
it publishes no `UiMessage`. Save remains the sole path that surfaces money validation errors.

## Consequences

### Positive

- Live and persisted R-2 calculations cannot drift.
- Incomplete or invalid typing remains non-disruptive until save.

### Negative

- Presentation becomes a direct consumer of one domain resolver inside the feature module.

### Constraints Introduced

- No second copy of R-2 formulas or monetary range constants may exist in presentation.
- Resolver errors during typing MUST leave the derived value null and `message` unchanged.

## Verification

- Domain tests keep the existing validator golden values.
- Presentation tests cover all three modes and error-without-message behavior.

## References

- `docs/SPECIFICATION.md §6` R-2
- `docs/CONTRACTS.md §2`, `§13`, `§20.5`
