# ADR-0208 / D-207 - Reset Stale `SYNCING` Rows from Their Outbox at Startup

## Status

Accepted

The owner selected option B on 2026-10-14.

## Context

`E3-21` adds the explicit startup reset that `docs/CONTRACTS.md §7` deliberately left out of the MVP.
If the process dies between `markVehicleSyncing` / `markFuelEntrySyncing` and the push's
acknowledgement or failure, the entity row stays `SYNCING`. Nothing is lost: the outbox row survives,
is reselected when due and re-pushed, and the aggregate `SyncStatus` derives from the outbox
(`§9.9`), which counts the stranded row as `Pending`. Only the entity row's own state is stale.

The acceptance criterion asks for a reset "to a state consistent with its outbox row". Two facts make
that phrase ambiguous:

- A `SYNCING` row always has an outbox row: the acknowledgement sets `SYNCED` and deletes the outbox
  row in one transaction, and a failure keeps it. The outbox row holds the outcome of the last
  resolved attempt in `lastErrorCode`.
- `failPush` already classifies an outcome into an entity state: a connectivity code gives `PENDING`
  and any other code gives `FAILED_RETRYABLE`; `§9.7` states that `§7` reserves `PENDING` for the
  `CONNECTIVITY_ERROR_CODES` case (or no error at all).

`markSyncing` never moves a `FAILED_POISONED` row, so a stranded row was `PENDING` or
`FAILED_RETRYABLE` before the interrupted attempt.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. Reset every stale `SYNCING` row to `PENDING` | One unconditional statement; the published aggregate does not change at startup. | Leaves `PENDING` rows carrying a non-connectivity `lastErrorCode`, the combination `§7` reserves away, until the next attempt resolves. |
| B. Restore the pre-attempt state from the outbox row, with the `failPush` rule | The row returns to exactly the state it had before the interrupted attempt; no forbidden combination is created; one classification rule for every path. | The connectivity codes appear as SQL literals in one more statement, under `ConnectivityCodeParityTest`. After a restart, a row whose last resolved attempt failed for a non-connectivity reason makes the aggregate `Failed` instead of `Pending`, which is the honest signal. |
| C. Document only | No code. | Fails the `E3-21` acceptance criterion. |

## Decision

The selected option is **B**.

- Every `vehicle` and `fuel_entry` row whose `syncState` is `SYNCING` returns to `FAILED_RETRYABLE`
  when its outbox row's `lastErrorCode` is non-null and not in `CONNECTIVITY_ERROR_CODES`, and to
  `PENDING` otherwise. `FAILED_POISONED` is never produced. A `SYNCING` row without an outbox row is
  not reachable (`§7`); the reset maps it to `PENDING` rather than leaving it `SYNCING`.
- The outbox row is not modified: `attemptCount`, `nextAttemptAt`, `lastError`, `lastErrorCode` and
  `cycleId` keep their values, so the backoff and the poison budget of `§9.7` are unchanged.
- The reset runs at most once per app start, under the sync cycle's mutual exclusion, so it never
  overlaps a push of the same process and can never reset a row that this process has just marked.
- It follows the `D-194` execution policy: a read-only count runs first and the write transaction
  opens only when the count is non-zero, so a start with nothing to reset never takes the SQLite
  write lock; the reset of both tables commits in one transaction; a failure other than cancellation
  is reported once as a non-fatal `UnexpectedError` and is not retried in the same process.

## Consequences

### Positive

- After a restart no entity row is `SYNCING` unless this process marked it.
- The entity state agrees with its outbox row under the same rule as `failPush`.

### Negative

- `§7` gains two startup-reset transitions, and its "no startup reset" paragraph is rewritten.
- A row whose last resolved attempt failed for a non-connectivity reason shows as `Failed` after a
  restart rather than `Pending`.

### Constraints Introduced

- The reset statements join `ConnectivityCodeParityTest`'s statement list, or classify in Kotlin from
  `CONNECTIVITY_ERROR_CODES`; the connectivity codes are never spelled in a statement the parity test
  does not check.
- The aggregate stays derived from the outbox, and push ordering is unchanged.

## Verification

- A database test seeds stale `SYNCING` rows of both tables with no error, a connectivity code and a
  non-connectivity code, runs the reset, and asserts `PENDING`, `PENDING` and `FAILED_RETRYABLE`, the
  untouched outbox rows, and one transaction that rolls back as a whole on failure.
- A count-gate test asserts that no write transaction opens when nothing is stale.
- An app-graph test seeds a stale `SYNCING` row and asserts its reset after the graph starts.
- A `:core:sync` test asserts the reset never runs while a push of the same process is in flight.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-207`)
- `docs/CONTRACTS.md §7`, `§9.7`, `§9.9`
- `D-194` (ADR-0195), the tombstone purge execution policy
- `docs/BACKLOG.md` (`E3-21`)
- `docs/handoffs/E3-03.md` (MINOR 4)
