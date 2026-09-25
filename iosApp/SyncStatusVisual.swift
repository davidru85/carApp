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

/// The visual of one published status. The counts inside a status are deliberately ignored: `§9.9`
/// already placed those rows in their bucket, so a host that read the counts would be re-deriving a
/// decision it does not own.
func syncStatusVisual(_ status: SyncSyncStatus) -> SyncStatusVisual {
    if status is SyncSyncStatus.Idle { return .idle }
    if status is SyncSyncStatus.Syncing { return .syncing }
    if status is SyncSyncStatus.Pending { return .pending }
    if status is SyncSyncStatus.Failed { return .failed }
    return .idle
}

/// Whether this visual is an error. `§9.9` makes `Failed` the only error condition, so this decides
/// the error styling and whether the indicator offers its manual retry.
func syncStatusIsError(_ visual: SyncStatusVisual) -> Bool {
    visual == .failed
}

/// The label of one visual. `Idle` states that nothing is outstanding rather than that the remote
/// copy is current, because `§9.9` defines `Idle` as an empty outbox and `§9.1` enqueues nothing at
/// all while the owner is `LOCAL_OWNER` (`D-193`).
func syncStatusLabelKey(_ visual: SyncStatusVisual) -> String {
    switch visual {
    case .idle:
        return "backup_status_idle"
    case .syncing:
        return "backup_status_syncing"
    case .pending:
        return "backup_status_pending"
    case .failed:
        return "backup_status_failed"
    }
}
