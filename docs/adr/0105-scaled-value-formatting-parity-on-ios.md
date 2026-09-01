# ADR-0105 / D-104 - Scaled Value Formatting Parity on iOS

## Status

Accepted

Selected by the owner on 2026-09-01 for story E1-09.

## Context

`CONTRACTS.md §2` specifies exact scaled integer representation for domain values:
- Fuel liters: scale 1,000 (3 decimal digits)
- Fuel price per liter: scale 1,000 (3 decimal digits)
- Consumption average: scale 100 (2 decimal digits)
- Monetary costs: scale 100 (2 decimal digits for EUR/USD/GBP cents)

Android UI formats these scaled values using pure decimal integer division and remainder formatting
with locale-appropriate decimal separators (or standard dots for numeric text inputs). To ensure
strict behavioral and visual parity between Android and iOS without floating-point conversion inaccuracies,
Swift UI formatting must replicate the exact same integer arithmetic and scale truncation rules.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Pure integer arithmetic formatting in Swift (`quotient` and zero-padded `remainder`), with strict scale constants (1000, 1000, 100, 100) | Exact parity with Android; zero IEEE-754 floating-point rounding errors; predictable string representation across all values. | Requires custom formatting helpers in Swift. |
| Convert scaled integers to `Double` or `Decimal` before formatting with `NumberFormatter` | Leverages Apple platform formatters directly. | `Double` introduces floating-point representation drift; `Decimal` is heavier and can produce diverging rounding behaviors compared to Android. |

## Decision

Implement pure integer arithmetic formatting in `ScaledFormatting.swift`:
- Format function divides integer value by `scale`: integer part is `value / scale`, fractional part is `abs(value % scale)`.
- Fractional digits are padded with leading zeros according to scale precision (3 digits for 1,000; 2 digits for 100).
- Scale constants match domain models:
  - `litersScale = 1000`
  - `pricePerLiterScale = 1000`
  - `consumptionScale = 100`
  - `minorUnitScale = 100`
- Parse function converts string to scaled integer by splitting on decimal separator (`.` or `,`) and padding/truncating fractional digits to exact scale digits.

## Consequences

### Positive

- Identical numeric representations displayed on Android and iOS across all test vectors.
- No floating-point rounding errors on financial or consumption metrics.
- Comprehensive unit test coverage in `ScaledFormattingTests`.

### Negative

- Requires maintaining `ScaledFormatting.swift` in the iOS host application.

### Constraints Introduced

- Swift formatting MUST NOT cast scaled integers to `Double` for display or calculations.
- Input parsing MUST accept both dot (`.`) and comma (`,`) as decimal separators.

## Verification

- `ScaledFormattingTests` verifies exact formatting and parsing across standard values, zero, boundary values, and multi-scale conversions.

## References

- `docs/CONTRACTS.md §2`
- `docs/BACKLOG.md` E1-09
