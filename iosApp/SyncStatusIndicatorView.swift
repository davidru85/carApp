import SwiftUI
import Shared

/// The discreet backup status indicator of `docs/SPECIFICATION.md §3.1`, drawn as the status chip the
/// iOS design already places on this screen (`docs/DESIGN.md §4`, screen 02 home).
///
/// It renders the value the shared layer publishes and nothing else: the precedence and the
/// connectivity rule belong to `docs/CONTRACTS.md §9.9`, and `§14` forbids this view from computing a
/// second status. The retry affordance appears only for the visual `§9.9` defines as an error, and it
/// calls the existing `SyncStateHolder.retryFailed()`, so a failure it produces arrives as the
/// holder's typed `UiMessage` and is rendered through the one existing mapping, `localizedText`,
/// and only beside the `Failed` visual, because `§9.9` reserves the error presentation for it.
///
/// The status text owns the accessible state; the coloured dot is decorative. The Retry button is
/// deliberately left as its own element: combining the row into one accessibility element would fold
/// a button into a non-actionable node and make manual recovery unreachable with a screen reader.
struct SyncStatusIndicatorView: View {
    let status: SyncSyncStatus
    let message: UiMessage?
    let onRetry: () -> Void

    private var visual: SyncStatusVisual { syncStatusVisual(status) }

    private var label: String {
        String(localized: String.LocalizationValue(syncStatusLabelKey(visual)))
    }

    private var isError: Bool { syncStatusIsError(visual) }

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 8) {
                // Decorative: the adjacent text carries the accessible status.
                Circle()
                    .fill(isError ? Color.red : Color.accentColor)
                    .frame(width: 8, height: 8)
                    .accessibilityHidden(true)

                Text(label)
                    .font(.footnote)
                    .foregroundColor(isError ? .red : .secondary)
                    .accessibilityIdentifier("backup_status_indicator")
                Spacer()
                if isError {
                    Button(String(localized: "backup_status_retry"), action: onRetry)
                        .font(.footnote)
                        .accessibilityIdentifier("backup_status_retry")
                }
            }

            if isError, let message {
                Text(message.localizedText)
                    .font(.footnote)
                    .foregroundColor(.red)
                    .accessibilityIdentifier("backup_status_error")
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
    }
}

/// Draws the indicator from the process model and observes it.
///
/// `VehicleListView` holds `WalkingSkeletonModel` as a plain reference. SwiftUI does not re-evaluate
/// a child whose stored references are unchanged, so a status change or a retry failure that
/// arrived without a vehicle-list change was never drawn. This row is the only observer of the model
/// on that screen, so a sync-only change redraws the indicator and nothing else. The status source
/// stays `SyncStateHolder.state`, as `docs/adr/0193` assigns to iOS.
struct BackupStatusRow: View {
    @ObservedObject var model: WalkingSkeletonModel

    var body: some View {
        SyncStatusIndicatorView(
            status: model.syncState.status,
            message: model.syncState.message,
            onRetry: model.retryBackup
        )
    }
}
