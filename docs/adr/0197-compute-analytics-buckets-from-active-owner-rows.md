# ADR-0197 - Compute the analytics count buckets from the owner's active rows

## Status

Accepted

## Context

`AnalyticsUserProperties` carries `vehicleCountBucket` and `entryCountBucket`, both `CountBucket`
values with exact bounds (`docs/CONTRACTS.md §20.9`). `§16.1` says the buckets "are computed from the
current list size". That sentence does not say whose list, over what scope, or whether tombstoned
rows count.

Three readings were available and each produces a different number for the same database:

- the owner's active rows in the local database;
- the list the UI currently has on screen, which is per-holder and, for fuel entries, per selected
  vehicle;
- the existing `countRowsOwnedBy` query, which counts rows regardless of `deleted`.

The choice matters for privacy as well as accuracy: a bucket is a coarse figure by design, and a
scope that includes tombstoned rows would report items the owner has deleted.

## Options Considered

| Option | Benefits | Costs or risks |
|--------|----------|----------------|
| Option A: count the owner's active rows in `:core:database` | One deterministic figure per owner, independent of which screen is open; tombstones excluded; the count has no UI dependency, so it works before any list is mounted | Adds two read-only queries and a read-only accessor to `:core:database`, the shared-write module, so the story must declare the `database-lock` |
| Option B: derive from presentation `StateFlow`s | Literally "the current list size"; no database change | Only exists while a list holder is alive; `entryCountBucket` becomes per selected vehicle rather than the owner's total; the value depends on which screen is open |
| Option C: reuse `countRowsOwnedBy` | No new code at all | Counts tombstoned rows, so it is not the size of the list the UI shows and it reports deleted items |

## Decision

The selected option is **Option A**.

- Two queries are added to `database.sq`: `countActiveVehiclesByOwner` and
  `countActiveFuelEntriesByOwner`, both filtered by `ownerId` and `deleted = 0`.
- They are exposed through a read-only accessor in `:core:database`; it opens no transaction and
  exposes no mutation.
- `:shared` maps the two counts through `CountBucket.ofCount`, so the bounds stay written once, in
  `:core:analytics`.
- The bucket figures are refreshed at the two moments `§16.1` names: at opt-in and after every
  successful vehicle or fuel-entry create/delete.

## Consequences

### Positive

- The buckets are deterministic and independent of the UI, so two devices with the same data report
  the same buckets.
- Tombstoned rows are excluded by construction, so a deleted vehicle never appears in a bucket.
- The count queries are read-only, so they cannot hold the SQLite write lock the way an
  unconditional write transaction would (`D-194` records that hazard).

### Negative

- `:core:database` gains two queries and a read-only accessor for an analytics consumer.
- A bucket refreshed after a write can race a concurrent write from another coroutine, so a bucket
  may lag by one write. `§16.1` requires a coarse figure, not a linearizable one, so this is
  accepted; no test asserts an exact count under concurrent writes.

### Constraints Introduced

- `countActiveVehiclesByOwner` and `countActiveFuelEntriesByOwner` MUST stay `deleted = 0` filtered
  and owner-scoped.
- The analytics count accessor MUST remain read-only: no transaction, no write scope.

## Verification

- `AnalyticsCountDatabaseAccessTest` seeds active and tombstoned rows on both tables and asserts the
  counts exclude the tombstones.
- `SharedAnalyticsCadenceTest` asserts the bucketing is applied through `CountBucket.ofCount`.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-197`)
- `docs/CONTRACTS.md §16.1`, `§20.9`
- `docs/TECHNICAL_PLAN.md §4`
