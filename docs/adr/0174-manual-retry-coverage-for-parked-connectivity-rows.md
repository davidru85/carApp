# ADR-0174 / D-173 - Manual Retry Coverage for Parked Connectivity Rows

## Status

Accepted

The `E3-03` sixth owner-review round raised this on 2026-09-13. No production fix is delivered by
`E3-03`; this ADR records the gap and the options. The owner selected **option B** on 2026-10-14,
with the premise correction recorded under "Decision"; `E3-18` delivers it inside bundle `E3-B1`
(`D-205`).

## Context

The `E3-03` R5 round made a connectivity-only failure leave the entity `syncState = PENDING`
(`docs/CONTRACTS.md §7`, `§9.9`), with its retry context held in the outbox. That is the correct row
state, but it opened a manual-retry gap.

`resetFailedOutbox` (`database.sq`) clears `attemptCount`, `nextAttemptAt`, `lastError`,
`lastErrorCode` and `cycleId` only for entities whose `syncState` is `FAILED_RETRYABLE` or
`FAILED_POISONED`. A connectivity-only row is now `PENDING`, so it is outside that selection.
`SyncController.retryFailed()` therefore has no effect on it.

`§9.7` justifies excluding connectivity rows from manual retry because "Connectivity-only failures
already auto-resume". That holds only for a real offline-to-online transition, which fires
`ConnectivityRecovered`. A server-side `REMOTE.UNAVAILABLE` or `REMOTE.DEADLINE_EXCEEDED` while the
device stays online produces no such trigger, so the row waits out its backoff — up to
`MAX_BACKOFF_MS` (900_000 ms) — the aggregate reports `Pending` rather than `Failed`, and the user
has no way to force it.

This is a bounded liveness gap, not a data-loss or correctness defect: the row does eventually
resume. The owner decides whether the manual escape hatch must cover it.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. Accept the current behaviour and bound it explicitly in `§9.7` as at most `MAX_BACKOFF_MS` | No code change; the gap is stated rather than implied. The row is guaranteed to become due within 15 minutes. | The user cannot force a row they can see is pending, and a server-side outage that keeps failing parks every affected row for up to the ceiling each time. The manual retry control appears to do nothing for such a row. |
| B. (recommended) Extend `retryFailed()` so it also clears the outbox retry context of rows whose `lastErrorCode` is in `CONNECTIVITY_ERROR_CODES`, regardless of entity `syncState` | The manual escape hatch covers every parked row, which is what the user expects from a retry control. `attemptCount` is reset to 0 on exactly those rows, matching the existing `retryFailed()` contract. It is a local statement change with no new trigger or gesture. | `retryFailed()`'s selection grows from an entity-state predicate to also include a `lastErrorCode` predicate, so its contract text and its test surface widen. A connectivity row's backoff is intentionally preserved by automatic recovery, so manual retry becomes the one path that resets it. |
| C. Make a `PullToRefresh` cycle call `markConnectivityFailuresDue` | No change to `retryFailed()`. | Couples a user gesture to a connectivity-specific step: a pull-to-refresh is a general refresh, not a connectivity event, and it would make every manual refresh mutate outbox scheduling for unrelated rows. It also inherits the same reachability question for the non-gesture case. |

## Recommendation

**Option B.** `retryFailed()` is the declared user-initiated recovery path, and after R5 it silently
does nothing for a class of rows the user can see as pending. Extending its outbox-reset selection to
connectivity codes regardless of entity state restores a complete manual escape hatch and keeps the
existing reset semantics. Option A is honest but leaves a dead-looking control; Option C couples a
general gesture to a connectivity-specific step.

## Decision

The selected option is **B**, accepted by the owner on 2026-10-14.

`resetFailedOutbox` also clears the outbox retry context (`attemptCount = 0`, `nextAttemptAt = now`,
`lastError`, `lastErrorCode`, `cycleId`) of every row whose `lastErrorCode` is in
`CONNECTIVITY_ERROR_CODES`, regardless of the entity `syncState`. The entity state of such a row is
not changed: it is already `PENDING`.

Premise correction. This ADR was written before `E3-05` delivered the backup status UI
(`D-191`–`D-193`). On both hosts the retry control is rendered only while the aggregate is `Failed`
(`SyncStatusIndicatorView` on Android and iOS), a connectivity-class failure publishes `Pending`
(`§9.9`), and neither host offers a pull-to-refresh gesture. The statements above that the control
"appears to do nothing" for such a row, and that `Pending` becomes "always actionable", therefore
overstate the effect. What option B changes is narrower:

- When at least one `FAILED_RETRYABLE` or `FAILED_POISONED` row makes the aggregate `Failed`, the
  retry control is shown, and pressing it now also makes every parked connectivity row due instead of
  leaving it to wait out its backoff.
- When the only stalled rows are parked connectivity rows, the aggregate is `Pending`, no retry
  control is shown under any option, and the row becomes due within `MAX_BACKOFF_MS` (900_000 ms).
  `§9.7` states that bound explicitly.

Showing the retry control while `Pending` was considered and rejected: it reopens the `E3-05`
presentation decisions and the `§9.9` rule that reserves the error presentation for `Failed`.
Option C was rejected also because no host exposes a pull-to-refresh gesture, so it would give the
user no path.

## Consequences

### Positive

- The manual retry control, whenever it is shown, makes every parked row due, including connectivity
  rows that are not themselves failed.
- The fix is bounded to `resetFailedOutbox` and the `retryFailed()` contract, with no new trigger.

### Negative

- `retryFailed()`'s selection and its tests widen; the contract text of `§9.7` must be updated with
  the chosen option.
- A parked connectivity row with no `Failed` row beside it still has no manual control; its wait is
  bounded by `MAX_BACKOFF_MS` and stated in `§9.7`.
- `resetFailedOutbox` repeats the connectivity codes as SQL literals, so it joins the statements
  `ConnectivityCodeParityTest` checks against `CONNECTIVITY_ERROR_CODES`.

### Constraints Introduced

- `E3-03` MUST NOT change production code for this item; it only records the decision and the story.
- The chosen option MUST keep the poison rule and `attemptCount` semantics of `§9.7` intact.
- If option A is chosen, `§9.7` MUST state the `MAX_BACKOFF_MS` bound explicitly and the residual
  user-visible effect.

## Verification

- Option B: a `SyncDatabaseAccessTest` case seeds a `PENDING` row with a connectivity `lastErrorCode`
  and a far-future `nextAttemptAt`, calls `resetFailed`, and asserts `attemptCount = 0`, cleared error
  context and a due row. A controller test asserts `retryFailed()` makes such a row due.
- Option A: a test asserting the row becomes due within `MAX_BACKOFF_MS` and that `retryFailed()`
  leaves it unchanged, with the `§9.7` bound stated.
- Option C: a test asserting a `PullToRefresh` cycle calls `markConnectivityFailuresDue`.

## References

- `docs/CONTRACTS.md §7`, `§9.7`, `§9.9`
- `docs/SPECIFICATION.md §2`, `§12`
- `docs/TECHNICAL_PLAN.md §2`, `§9`
- `docs/BACKLOG.md` (`E3-03`, `E3-18`)
- `docs/handoffs/E3-03.md`
