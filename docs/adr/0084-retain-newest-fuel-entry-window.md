# ADR-0084 / D-83 - Retain the Newest Fuel Entry Projection Window

## Status

Accepted

## Context

The MVP caps each per-Vehicle Fuel Entry projection at `MAX_ENTRIES_IN_MEMORY = 5_000`. Applying
that limit directly to ascending list and calculation order retained the oldest chronological rows
and the lowest odometers. Once a Vehicle crossed the cap, new list entries disappeared and the
consumption report stopped advancing.

The cap still protects memory, and both projection consumers still require the deterministic
ascending order in `docs/CONTRACTS.md §4`.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Select the highest canonical-order window in a descending inner query, then restore canonical ascending order outside | Retains current data under the same memory bound; keeps deterministic consumer ordering; uses one database query per projection. | Each query has two ordering stages. |
| Keep applying the limit directly to ascending order | Simplest query and preserves returned ordering. | Permanently discards new rows after the cap and freezes consumption on stale data. |
| Remove the cap or raise it until the defect is unlikely | Avoids window selection logic. | Violates the explicit memory ceiling, does not define behavior at the next cap, and increases cross-platform memory risk. |

## Decision

Both SQLDelight projection queries select their bounded window through a descending inner query and
return that window through an ascending outer ordering.

- The list selects by `date DESC, createdAt DESC, id DESC` and returns
  `date ASC, createdAt ASC, id ASC`.
- Consumption selects by `odometerKm DESC, date DESC, id DESC` and returns
  `odometerKm ASC, date ASC, id ASC`.

Production continues to bind `MAX_ENTRIES_IN_MEMORY`. `SqlDelightFuelEntryLocalDataSource` accepts
an internal constructor limit solely so common tests can prove the same behavior with three rows;
the public repository constructor uses the default constant.

## Consequences

### Positive

- Newly created entries remain observable after the per-Vehicle cap.
- Consumption continues to incorporate the highest calculation-order rows.
- Downstream mapping and calculation receive the unchanged canonical ascending order.
- Tests prove the cap without materialising 5,000 rows.

### Negative

- SQLite performs an inner descending selection and an outer ascending ordering.
- A bounded consumption report intentionally excludes the lowest-odometer history once capped.

### Constraints Introduced

- Production MUST bind `MAX_ENTRIES_IN_MEMORY` to both projection queries.
- The selected window MUST be the highest complete canonical-order range.
- The returned list ordering MUST remain `date ASC, createdAt ASC, id ASC`.
- The returned consumption ordering MUST remain `odometerKm ASC, date ASC, id ASC`.

## Verification

- Common tests inject a limit of three and insert more than three rows.
- List evidence proves the newest rows survive and remain chronological.
- Consumption evidence proves the highest odometers survive and remain in calculation order.
- SQLDelight generation and compilation prove the hand-written data-source call sites retain their
  signatures.

## References

- `docs/CONTRACTS.md §4`, `§12`, `§20.0.1`
- ADR-0079 / D-78
- ADR-0080 / D-79
- `docs/BACKLOG.md` E1-06
