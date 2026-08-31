# ADR-0093 / D-92 - Preserve Fuel Entry Row Indicators

## Status

Accepted

Accepted by the owner for E1-08 on 2026-08-31.

## Context

E1-08 must render `hasMissedEntries` and `odometerInconsistent` independently on every row,
including a partial refuel whose primary no-consumption reason is `EndEntryNotFullTank`.
`LocalFuelEntry` already carries both facts, but the canonical `FuelEntryListItem` projection drops
them, so presentation cannot satisfy the requirement without another data path.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Add both flags to `FuelEntryListItem` and map them from `LocalFuelEntry` | Keeps one reactive, ordered projection and preserves overlapping facts exactly. | Changes the canonical read model and its contract. |
| Add a parallel indicator projection and combine it in presentation | Leaves the existing row type unchanged. | Requires two observations, ID reconciliation and transient consistency handling. |
| Load the full Fuel Entry once per displayed row | Reuses `getFuelEntry`. | Creates N+1 reads, is not reactive and scales poorly. |

## Decision

Add `hasMissedEntries: Boolean` and `odometerInconsistent: Boolean` to `FuelEntryListItem`.
`LocalFuelEntry.toFuelEntryListItem` maps both values directly. No SQLDelight schema, query or
database-access change is introduced.

## Consequences

### Positive

- Every row retains both independent warning facts even when another invalid reason has precedence.
- The UI keeps one owner-scoped reactive list source.

### Negative

- `:core:model` and the canonical type declaration gain two fields.

### Constraints Introduced

- A partial row MUST retain both flags while its `invalidReason` remains `EndEntryNotFullTank`.
- No additional query or per-row repository read may be added for these indicators.

## Verification

- Core-model and projection tests prove both flags survive on a partial row.
- Existing list ordering and consumption-projection tests remain green.

## References

- `docs/BACKLOG.md` E1-08
- `docs/CONTRACTS.md §20.4`, `§20.10`
