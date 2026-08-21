# ADR-0027 / D-26 - Correct the Contradictory Monetary Golden Row

## Status

Accepted

Accepted by the owner on 2026-08-21.

## Context

`docs/CONTRACTS.md §2` states the three canonical monetary formulas and says they MUST be implemented literally, then gives four golden rows that MUST be covered by tests in `:core:model`.

`E0-03` implemented the formulas and found that golden row 3 contradicts the formula in the same section. For `litersScaled = 1`, `pricePerLiterScaled = 1`, EUR the row expected `totalCostMinor = 1`, annotated "rounds up from 0.0001". The formula gives `0`:

```text
(1 * 1 * 100 + 500_000) / 1_000_000 = 500_100 / 1_000_000 = 0
```

The formula is right and the row was wrong. 0.001 L at 0.001 €/L is 0.000001 €, which is 0.0001 minor units, and HALF_UP of 0.0001 is 0. The row's parenthetical "(0.01 €)" is one cent, ten thousand times the real value. The other three rows agree with the formula exactly, including the overflow row.

The row's purpose was to prove that rounding **up** happens, so simply changing `1` to `0` would have removed that coverage rather than fixed it.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Correct the row to `0` and add a genuine HALF_UP round-up row | Keeps the regression coverage the row was meant to provide, and the formula stays untouched. | Two table edits instead of one. |
| Correct the row to `0` | Smallest edit. | Loses the round-up coverage; the table would then prove only rounding down. |
| Add a "minimum one minor unit" rule so the row becomes true | No non-zero purchase would ever cost zero. | Contradicts HALF_UP, changes the canonical formula, affects every small amount, and no product requirement asks for it. |

## Decision

Correct golden row 3 to `0`, and add a row that demonstrates HALF_UP rounding up at the exact halfway point: `litersScaled = 1_000`, `pricePerLiterScaled = 5`, EUR gives `totalCostMinor = 1`, because the exact value is 0.5 minor units.

## Consequences

### Positive

- The golden table and the formula agree, so "implement the formula literally" is satisfiable.
- Both rounding directions remain covered by a regression test.

### Negative

- A very small purchase can legitimately round to zero cost. That follows from HALF_UP and is now explicit in the table rather than contradicted by it.

### Constraints Introduced

- The monetary formulas stay as written in `§2`; no minimum-amount rule exists.

## Verification

- `MonetaryArithmeticTest` covers every row of the corrected table, including both new rows.

## References

- `docs/DECISION_BOARD.md` (`D-26`)
- `docs/CONTRACTS.md §2`
- `docs/handoff-E0-03.md`
