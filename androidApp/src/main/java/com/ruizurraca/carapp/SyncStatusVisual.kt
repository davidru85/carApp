package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.common.SyncStatus

/**
 * How the host draws the aggregate the shared layer has already resolved.
 *
 * `docs/CONTRACTS.md §9.9` owns the precedence `Failed > Syncing > Pending > Idle` and the rule that
 * an offline owner with pending rows, and a failure whose every row carries a connectivity code,
 * count as `Pending` and never as an error. `§14` requires every holder to relay the single
 * `SyncController.status` source, so the host receives one resolved value and MUST NOT recompute
 * either rule. This mapping is therefore total over `SyncStatus` and takes no connectivity fact,
 * which makes "offline means error" unrepresentable here instead of merely untested.
 */
internal enum class SyncStatusVisual { IDLE, SYNCING, PENDING, FAILED }

/**
 * The visual of one published status. Every branch is a `§9.9` aggregate value, and the counts
 * inside a status are deliberately ignored: `§9.9` already placed those rows in their bucket, so a
 * host that read the counts would be re-deriving a decision it does not own.
 */
internal fun syncStatusVisual(status: SyncStatus): SyncStatusVisual =
    when (status) {
        is SyncStatus.Idle -> SyncStatusVisual.IDLE
        is SyncStatus.Syncing -> SyncStatusVisual.SYNCING
        is SyncStatus.Pending -> SyncStatusVisual.PENDING
        is SyncStatus.Failed -> SyncStatusVisual.FAILED
    }

/**
 * Whether this visual is an error. `§9.9` makes `Failed` the only error condition, so this decides
 * the error colour and whether the indicator offers its manual retry. It is a comparison of the
 * published classification rather than of a `SyncStatus`, so no caller can reach the counts.
 */
internal fun syncStatusIsError(visual: SyncStatusVisual): Boolean = visual == SyncStatusVisual.FAILED

/**
 * The label of one visual. `Idle` states that nothing is outstanding rather than that the remote
 * copy is current, because `§9.9` defines `Idle` as an empty outbox and `§9.1` enqueues nothing at
 * all while the owner is `LOCAL_OWNER` (`D-193`).
 */
internal fun syncStatusLabelResource(visual: SyncStatusVisual): Int =
    when (visual) {
        SyncStatusVisual.IDLE -> R.string.backup_status_idle
        SyncStatusVisual.SYNCING -> R.string.backup_status_syncing
        SyncStatusVisual.PENDING -> R.string.backup_status_pending
        SyncStatusVisual.FAILED -> R.string.backup_status_failed
    }
