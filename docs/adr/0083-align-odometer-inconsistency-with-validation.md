# ADR-0083 / D-82 - Align Persisted Odometer Inconsistency with Validation

## Status

Accepted

## Context

Fuel Entry validation warns when an entered odometer is strictly below the Vehicle initial
odometer or at or below the previous active chronological neighbour. Before D-82, the database
cached `odometerInconsistent` from only the neighbour branch. A confirmed first-entry warning could
therefore be stored as `false`, and the post-write outbox snapshot preserved the same incorrect
value.

The database schema deliberately has no enforced Fuel Entry-to-Vehicle foreign key because remote
recovery can deliver a Fuel Entry before its Vehicle. The derivation must therefore align with
validation without making orphan recomputation fail or treating absence as inconsistency.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Derive from the neighbour branch OR the Vehicle initial-odometer branch inside `:core:database`, with a missing-Vehicle fallback | Matches validation exactly; preserves confirmed warnings in local and remote snapshots; keeps the database-owned sole-writer rule; remains safe for orphans. | The SQL expression needs a nullable Vehicle lookup and explicit null defense. |
| Restrict the §5 persisted-warning requirement to the neighbour branch | Matches the existing implementation with no code change. | Adapts the contract to a defect and leaves a confirmed initial-odometer warning with no persisted trace. |
| Pass the confirmed warning result into `:feature:fuel` and write the column directly | Makes the command path explicit. | Violates the database-owned invariant, duplicates derivation across feature and sync writers, and fails the architecture boundary. |

## Decision

For every active row in the existing §3.1 recompute set, `:core:database` sets
`odometerInconsistent` when either:

1. a previous active chronological neighbour exists and `odometerKm <= previous.odometerKm`; or
2. a Vehicle row exists and `odometerKm < vehicle.initialOdometerKm`.

The comparison operators are intentionally different. A missing Vehicle makes only the second
branch false, so an orphan falls back to the neighbour comparison. The SQL expression keeps an
outer `COALESCE(..., 0)` and never stores `NULL`.

The recompute set does not change. `initialOdometerKm` is editable only while the Vehicle has no
active Fuel Entries, so no new Vehicle-write recompute trigger is required.

## Consequences

### Positive

- Persisted and enqueued snapshots retain every confirmed odometer warning.
- Validation and database derivation share the same two predicates.
- Orphan Fuel Entries remain ingestible and recomputable.
- `:core:database` remains the only writer of the derived column.

### Negative

- Recomputing a flag performs one additional indexed Vehicle lookup.
- The SQL expression must preserve three-valued null behavior explicitly.

### Constraints Introduced

- The neighbour comparison MUST be `<=` and the initial-odometer comparison MUST be `<`.
- Missing Vehicle data MUST disable only the initial-odometer branch.
- Feature and sync code MUST NOT write `odometerInconsistent` directly.
- The §3.1 recompute set MUST remain unchanged by this decision.

## Verification

- Repository tests cover a confirmed first entry below the Vehicle initial odometer in both the
  stored row and its outbox payload.
- Boundary tests cover equality with the Vehicle initial odometer.
- Database tests cover a missing Vehicle and retain the existing neighbour-based cases.
- Android-host and `iosSimulatorArm64` execute the common behavior suite.

## References

- `docs/SPECIFICATION.md §6` R-1
- `docs/CONTRACTS.md §3.1`, `§5`, `§8`
- ADR-0078 / D-77
- `docs/BACKLOG.md` E1-06
