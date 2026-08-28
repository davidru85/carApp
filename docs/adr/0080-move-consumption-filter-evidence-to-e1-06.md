# ADR-0080 / D-79 - Move Consumption Repository-Filter Evidence to E1-06

## Status

Accepted

## Context

The original E1-05 backlog included acceptance evidence that `observeConsumption` passes only
non-deleted entries for one vehicle to `CalculateConsumption`. E1-05 owns the pure calculator,
while E1-06 owns the production `FuelEntryRepository` implementation and its dedicated projection
query. The canonical contract also states that the repository filters and the use case itself does
not.

Testing a fake repository in E1-05 would prove only that fake and could remain green if E1-06
implemented the production filter incorrectly. Pulling the real repository forward would mix the
pure story with SQLDelight work and touch the gated `core/database/**` path before its owning
story.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Move the production criterion to E1-06 and prove no filtering in E1-05 | Places evidence with its real implementation; preserves story and database ownership; makes the calculator precondition executable. | Production filter evidence arrives one story later. |
| Pull the minimum repository implementation into E1-05 | Satisfies the original backlog wording immediately. | Invades E1-06, crosses the database gate and makes E1-05 no longer a pure calculation story. |
| Test a filtering fake in E1-05 | Requires no database implementation. | Gives false confidence because it does not constrain the later production repository. |

## Decision

The repository-filter criterion moves verbatim from E1-05 to E1-06. E1-05 instead proves that
`CalculateConsumption` does not filter directly supplied entries by `vehicleId` or `deletedAt`.
E1-06 will prove that its production `observeConsumption` path supplies only non-deleted entries
for the requested vehicle.

## Consequences

### Positive

- Every acceptance test constrains production code owned by its story.
- The pure use case keeps the exact precondition and responsibility split of the contract.
- No database module or query is pulled into E1-05.

### Negative

- E1-05 cannot provide the production repository-filter evidence by itself.
- E1-06 carries an additional explicit acceptance test.

### Constraints Introduced

- E1-05 tests include entries from another vehicle and an entry with non-null `deletedAt`, and
  prove they participate when passed directly.
- E1-06 cannot close without a production `observeConsumption` filter test.

## Verification

- The backlog shows the repository criterion moved verbatim to E1-06.
- E1-05 common tests prove the calculator performs no filtering.
- The E1-05 handoff names the exact E1-06 follow-up evidence.

## References

- `docs/CONTRACTS.md §4`, `§12`, `§13`, `§20.6`
- `docs/BACKLOG.md` (`E1-05`, `E1-06`)
