import SwiftUI
import Shared

struct DiagnosticsView: View {
    @ObservedObject var model: WalkingSkeletonModel

    var body: some View {
        List {
            Section(header: Text("diagnostics_title")) {
                Text(
                    String.localizedStringWithFormat(
                        String(localized: "session_status"),
                        model.sessionLabel
                    )
                )
                .accessibilityIdentifier("session_status")

                if model.canStartAnonymousSession {
                    Button(String(localized: "continue_without_account")) {
                        model.startAnonymousSession()
                    }
                    .accessibilityIdentifier("continue_without_account")
                    .disabled(model.sessionState.isBusy)
                }

                if model.sessionState.isBusy {
                    ProgressView()
                }

                Button(String(localized: "restore_backup")) {
                    model.restoreBackup()
                }
                .accessibilityIdentifier("restore_backup")
            }
        }
        .navigationTitle(Text("diagnostics_title"))
    }
}
