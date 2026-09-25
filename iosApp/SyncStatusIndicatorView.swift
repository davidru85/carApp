import SwiftUI
import Shared

/// The discreet backup status indicator of `docs/SPECIFICATION.md §3.1`, drawn as the status chip the
/// iOS design already places on this screen (`docs/DESIGN.md §4`, screen 02 home).
///
/// It renders the value the shared layer publishes and nothing else: the precedence and the
/// connectivity rule belong to `docs/CONTRACTS.md §9.9`, and `§14` forbids this view from computing a
/// second status. The retry affordance appears only for the visual `§9.9` defines as an error, and it
/// calls the existing `SyncStateHolder.retryFailed()`, so a failure it produces surfaces through the
/// holder's typed `UiMessage` and needs no channel of its own.
struct SyncStatusIndicatorView: View {
    let status: SyncSyncStatus
    let onRetry: () -> Void

    private var visual: SyncStatusVisual { syncStatusVisual(status) }

    private var label: String {
        String(localized: String.LocalizationValue(syncStatusLabelKey(visual)))
    }

    private var isError: Bool { syncStatusIsError(visual) }

    var body: some View {
        HStack(spacing: 8) {
            // Decorative: the label beside it carries the state, and the row exposes it as one
            // accessibility element below rather than as a bare coloured box.
            Circle()
                .fill(isError ? Color.red : Color.accentColor)
                .frame(width: 8, height: 8)
                .accessibilityHidden(true)
            Text(label)
                .font(.footnote)
                .foregroundColor(isError ? .red : .secondary)
            Spacer()
            if isError {
                Button(String(localized: "backup_status_retry"), action: onRetry)
                    .font(.footnote)
                    .accessibilityIdentifier("backup_status_retry")
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("backup_status_indicator")
    }
}
