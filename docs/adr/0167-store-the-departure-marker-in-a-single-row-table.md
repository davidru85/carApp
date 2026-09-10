# ADR-0167 / D-166 - Store the Departure Marker in a Single-Row Table

## Status

Accepted

## Context

`D-165` closes the departure process-death window, which requires persisting what a departure has
already done before it does anything destructive. At most one departure is ever in flight, and the
facts a relaunch needs are small: which kind of departure it is, whose it is, and which of its
required steps have succeeded.

One constraint is not obvious. An anonymous local-data deletion clears local data and only then ends
the provider session, so the marker that records the second step has to survive the first.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. A single-row schema v4 table with one column per step | Follows the `D-151` precedent; each step is readable and writable on its own, so a relaunch repeats exactly what is owed; `CHECK` constraints close the kind at the database level | A schema version, a committed migration and a migration test |
| B. One opaque serialized marker row | No per-step schema | The whole value is rewritten for every step, and a partially written value is unreadable rather than partially usable |
| C. Reuse the `D-151` account-conversion tables | No new table | Two unrelated operations would share one marker and could not be in flight independently; the conversion marker is cleared by its own flow |
| D. Platform key-value storage | Survives a database wipe | Introduces a new platform abstraction for one row, outside the database whose transaction the local clear already uses |

## Decision

The selected option is **A**. `account_departure_operation` holds `kind`, `ownerUid` and one flag per
step, pinned to `id = 0`, added by the additive `3.sqm` migration.

`LocalDataClearDatabaseAccess.clearAllLocalData()` deliberately does not touch it. The marker is
operation control state, not owner data, in the same sense as `local_sequence`.

## Consequences

### Positive

- A relaunch can tell exactly which steps are owed, in the order the kind performs them.
- An unknown kind is a database error rather than a silently unhandled branch.
- An anonymous deletion can record its clear and still be resumed for its session cleanup.

### Negative

- The local schema now carries a second piece of process state, after the `D-151` conversion marker.

### Constraints Introduced

- The table MUST stay device-local: never synchronized, never enqueued in the outbox, and absent from
  the closed remote schema of `docs/CONTRACTS.md §16`.
- `clearAllLocalData()` MUST NOT delete it; the departure flow removes it when the departure ends.
- Starting a departure replaces any stale marker outright, because a new departure owes every step
  again.

## Verification

- `AccountDepartureDatabaseAccessTest` covers the round trip, per-step recording, replacement of a
  stale marker, and `theDepartureMarkerSurvivesTheLocalDataClear`.
- `AnonymousReminderMigrationTest` migrates a populated version-three database to version four and
  asserts row preservation plus the new empty table, with `verifyMigrations` enabled in CI.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-166`)
- `docs/CONTRACTS.md §11.5`, `§16`
- `docs/TECHNICAL_PLAN.md §2`, `§6`
- `docs/adr/0152-store-the-conversion-marker-in-normalized-tables.md`
- `docs/handoff-E2-05.md`
