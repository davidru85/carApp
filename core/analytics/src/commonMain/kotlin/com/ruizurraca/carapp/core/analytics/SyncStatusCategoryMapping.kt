package com.ruizurraca.carapp.core.analytics

import com.ruizurraca.carapp.core.common.SyncStatus

/**
 * The `SyncStatus -> SyncStatusCategory` collapse of `docs/CONTRACTS.md §20.9`.
 *
 * `§20.9` defines it as the plain rename **qualified by the connectivity rule of `§9.9`**:
 * `Idle -> IDLE`, `Syncing -> SYNCING`, `Pending -> PENDING`, and `Failed -> FAILED` only when at
 * least one counted row is not a connectivity-class retry; otherwise `Failed -> PENDING`.
 *
 * The qualification is applied once, where the aggregate is derived, by `:core:sync`
 * (`SyncEngine.refreshStatus`, `§9.9` "cycle-level failures"): the published retryable and poisoned
 * counts already exclude connectivity-class rows, and a connectivity-only cycle failure falls
 * through to the row-derived buckets instead of publishing `Failed`. A published
 * `SyncStatus.Failed` therefore entails a non-connectivity condition, which is why this collapse is
 * total and takes no connectivity parameter — a second connectivity observation here would be a
 * second implementation of the aggregate (`§14`, `D-192`, `D-193`).
 */
fun SyncStatus.toSyncStatusCategory(): SyncStatusCategory =
    when (this) {
        SyncStatus.Idle -> SyncStatusCategory.IDLE
        SyncStatus.Syncing -> SyncStatusCategory.SYNCING
        is SyncStatus.Pending -> SyncStatusCategory.PENDING
        is SyncStatus.Failed -> SyncStatusCategory.FAILED
    }
