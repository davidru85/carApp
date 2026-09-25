import Foundation
import Shared

/// How the host draws the aggregate the shared layer has already resolved.
///
/// `docs/CONTRACTS.md §9.9` owns the precedence `Failed > Syncing > Pending > Idle` and the rule that
/// an offline owner with pending rows, and a failure whose every row carries a connectivity code,
/// count as `Pending` and never as an error. `§14` requires every holder to relay the single
/// `SyncController.status` source, so the host receives one resolved value and MUST NOT recompute
/// either rule. This mapping is therefore total over `SyncSyncStatus` and takes no connectivity fact,
/// which makes "offline means error" unrepresentable here instead of merely untested.
enum SyncStatusVisual {
    case idle
    case syncing
    case pending
    case failed
}

func syncStatusVisual(_ status: SyncSyncStatus) -> SyncStatusVisual {
    // RED: declared without behaviour so the classification test compiles and executes.
    return .idle
}

/// Whether this visual is an error. `§9.9` makes `Failed` the only error condition, so the indicator
/// uses this to decide the error styling and whether it offers the manual retry.
func syncStatusIsError(_ visual: SyncStatusVisual) -> Bool {
    // RED: declared without behaviour so the classification test compiles and executes.
    return false
}

/// The label of one visual, resolved from the application catalogues. `UiState` carries no
/// user-facing text, so the host owns the mapping from visual to copy.
func syncStatusLabelKey(_ visual: SyncStatusVisual) -> String {
    // RED: declared without behaviour so the copy test compiles and executes.
    return "error_unexpected"
}
