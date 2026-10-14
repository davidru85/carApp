package com.ruizurraca.carapp.core.analytics

import com.ruizurraca.carapp.core.common.SyncStatus

/**
 * Direct, total rename of the published aggregate (`docs/CONTRACTS.md §20.9`, `D-202`).
 *
 * Only `:core:sync` applies the connectivity rule of `§9.9`. A connectivity-only cycle failure
 * falls through to row-derived status: Pending with pending work, Idle with an empty outbox.
 * Non-connectivity cycle failures can publish Failed with no failed rows, using a representative
 * retryable count of at least one. Analytics preserves each published shape and reads no row errors
 * or second connectivity observation (`§14`, `D-192`, `D-193`).
 */
fun SyncStatus.toSyncStatusCategory(): SyncStatusCategory =
    when (this) {
        SyncStatus.Idle -> SyncStatusCategory.IDLE
        SyncStatus.Syncing -> SyncStatusCategory.SYNCING
        is SyncStatus.Pending -> SyncStatusCategory.PENDING
        is SyncStatus.Failed -> SyncStatusCategory.FAILED
    }
