# ADR-0195 - Execute the tombstone purge as one count-gated attempt per app start

## Status

Accepted

## Context

`docs/CONTRACTS.md §8` states the local tombstone purge: a tombstone is purgeable only when
`syncState == SYNCED`, `serverUpdatedAt` is older than 90 days and no outbox row exists for it, and the
purge runs at most once per app start, in one transaction, in `:core:sync`. `E3-07` implements it. Three
execution details were left open by that sentence and had to be chosen while implementing it.

The first is the write lock. The driver opens every `database.transaction { }` with `BEGIN IMMEDIATE`
(`AndroidxSqliteExecutingDriver.Transaction`), which takes the file's write lock as soon as the
transaction starts, and the bundled SQLite stack sets no `busy_timeout`. A `DELETE` that matches zero
rows takes that lock exactly like one that deletes rows. The first implementation opened the purge
transaction unconditionally, so every app start became a writing transaction. On the first run of pull
request #78, `ios-simulator-build` failed `ViewModelLifecycleTests.testVehicleListConfirmDeleteAfterRequestDeletesVehicle`:
that suite mounts two graphs over one persistent `carapp.db`, and the second graph's save met the held
lock and failed with `SQLITE_BUSY` instead of waiting.

The second is the meaning of "at most once per app start" when the attempt fails. The first
implementation set its latch only after a successful purge, which allowed a second call in the same app
start to run the purge again.

The third is the age boundary. "Older than 90 days" does not by itself say how a tombstone exactly 90
days old, or one whose `serverUpdatedAt` is `NULL`, is treated.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Option A: read `countPurgeableVehicleTombstones` and `countPurgeableFuelEntryTombstones` outside any transaction and open the transaction only when their sum is non-zero; the `DELETE` statements repeat the full predicate | An app start with nothing to purge takes no write lock. No driver, schema or test-topology change. A row that changes between the count and the transaction is re-evaluated inside it | The predicate is written twice, so the count and the `DELETE` can drift; a test must pin both. A purge that does have work still takes the writer |
| Option B: keep the unconditional transaction and set a `busy_timeout` on every driver connection | One statement per table; a contending writer waits instead of failing | Changes the lock behaviour of every write path in `:core:database`. It is a database-stack decision well beyond `E3-07`, and it only bounds the wait rather than removing a writer that does no work |
| Option C: keep the unconditional transaction and stop `ViewModelLifecycleTests` mounting two graphs on one file | No production change | Hides the hazard instead of removing it: every app start still holds the writer for a no-op |

## Decision

The selected option is: **Option A**, together with two clarifications of `§8`.

- `SyncDatabaseAccess.purgeConfirmedTombstones(cutoff)` reads the two counts first and returns without
  opening a transaction when their sum is zero. Otherwise it runs `purgeConfirmedVehicleTombstones` and
  `purgeConfirmedFuelEntryTombstones` in one transaction.
- "At most once per app start" counts attempts. `TombstonePurge` sets its latch under a mutex before the
  attempt. A failure is converted to `UnexpectedError(":core:sync", <class name>)`, reported through the
  injected `onFailure`, which the app graph binds to `CrashReporter.recordNonFatal`, and retried only by
  the next app start.
- "Older than 90 days" is strict: a tombstone is purgeable only when
  `serverUpdatedAt < AppClock.now() - 90 days`. A tombstone whose `serverUpdatedAt` is `NULL` is never
  purgeable, because its age cannot be established.

## Consequences

### Positive

- An app start with nothing to purge is a pure read and cannot fail a concurrent writer.
- A purge failure never escapes into the graph scope, is reported once, and never runs twice in one app
  start.
- The boundary and the `NULL` case are stated once, in `§8`, instead of only in a test.

### Negative

- Each purge predicate exists twice in `database.sq`. A drift between a count and its `DELETE` either
  takes the writer for nothing or skips purgeable rows.
- A purge that has work to do still opens a `BEGIN IMMEDIATE` transaction and can meet a second
  connection's lock. `D-89` and `D-182` keep one graph and one driver per process, so this needs a
  second connection on the same file, which only the iOS UI test harness creates today.
- A tombstone confirmed through the `§6` `NotFound`-on-push path is `SYNCED` with
  `serverUpdatedAt = NULL`, so it is never purged locally.

### Constraints Introduced

- `countPurgeableVehicleTombstones` / `purgeConfirmedVehicleTombstones` and
  `countPurgeableFuelEntryTombstones` / `purgeConfirmedFuelEntryTombstones` MUST keep identical
  predicates.
- The purge latch MUST be set before the attempt, and a failed attempt MUST NOT be retried in the same
  app start.
- The age comparison MUST stay strict, and a `NULL` `serverUpdatedAt` MUST NOT be purgeable.

## Verification

- `TombstonePurgeDatabaseAccessTest` seeds every unpurgeable shape (younger, exactly at the cutoff,
  `NULL` timestamp, all four non-`SYNCED` states, an outbox row, an active row) on both tables. It
  asserts that both counts are zero for those rows alone, then purges them beside a purgeable anchor, so
  both `DELETE` statements run and must keep them. Removing the `deleted`, `syncState`, age or outbox
  guard from any one of the four statements, or relaxing the strict comparison to `<=`, fails at least
  one test. The `serverUpdatedAt IS NOT NULL` guard is redundant in SQL, where `NULL < :cutoff` is never
  true, and is kept only to state the rule explicitly.
- `AndroidTombstonePurgeWriteLockTest` and `IosTombstonePurgeWriteLockTest` hold the write lock on a
  second connection and prove an empty purge still succeeds.
- `TombstonePurgeTest.aFailedPurgeIsReportedOnceAndIsNotRetriedInTheSameAppStart` proves the failure
  path and the attempt latch.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-194`)
- `docs/SPECIFICATION.md §9`
- `docs/CONTRACTS.md §6`, `§8`, `§20.3.1`
- `docs/TECHNICAL_PLAN.md §2`
- `docs/handoff-E3-07.md`
