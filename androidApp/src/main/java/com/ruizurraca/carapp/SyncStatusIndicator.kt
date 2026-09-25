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

@Suppress("FunctionOnlyReturningConstant", "UnusedParameter")
internal fun syncStatusVisual(status: SyncStatus): SyncStatusVisual {
    // RED: declared without behaviour so the classification test compiles and executes.
    return SyncStatusVisual.IDLE
}

/** The label of one visual. It is a platform resource, never a field of `UiState`. */
@Suppress("FunctionOnlyReturningConstant", "UnusedParameter")
internal fun syncStatusLabelResource(visual: SyncStatusVisual): Int {
    // RED: declared without behaviour so the copy test compiles and executes.
    return R.string.error_unexpected
}
