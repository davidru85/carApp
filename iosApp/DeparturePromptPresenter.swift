import Shared
import SwiftUI

/// Presents the F-5 departure prompts over the signed-in surface (`B1-D3`, `B1-D5`, `D-158`).
///
/// The prompts are owned by `SessionStateHolder` and arrive as its typed `message`; this modifier only
/// decides how one is drawn (`departurePrompt(for:)`) and which intent each button calls. It is
/// attached at the root of the signed-in surface, above the vehicle list's navigation stack, so a
/// prompt raised from the pushed Settings screen and an outcome published after the departure screen
/// handed control back are both drawn by the same presenter, with no modal between them and the owner.
///
/// The presented value is a local copy refreshed from the derived prompt. An alert button dismisses its
/// alert before the holder's next emission arrives, and the local copy lets the next prompt of a
/// two-step protocol (a discard, then the destructive confirmation) present as a new alert.
struct DeparturePromptPresenter: ViewModifier {
    @ObservedObject private var model: WalkingSkeletonModel
    @State private var presented: DeparturePrompt?

    init(model: WalkingSkeletonModel) {
        _model = ObservedObject(wrappedValue: model)
    }

    private var current: DeparturePrompt? {
        departurePrompt(for: model.sessionState)
    }

    private var isPresented: Binding<Bool> {
        Binding(
            get: { presented != nil },
            set: { if !$0 { presented = nil } }
        )
    }

    func body(content: Content) -> some View {
        content
            .onAppear { presented = current }
            .onChange(of: current) { presented = $0 }
            .alert(
                Text(LocalizedStringKey(presented.map(departurePromptTitleKey) ?? "")),
                isPresented: isPresented,
                presenting: presented
            ) { prompt in
                actions(for: prompt)
            } message: { prompt in
                Text(departurePromptMessage(prompt))
            }
    }

    @ViewBuilder
    private func actions(for prompt: DeparturePrompt) -> some View {
        switch prompt {
        case let .pendingSync(_, intent):
            // F-5: wait for sync, cancel, or discard after a destructive confirmation (`B1-D3`).
            Button(String(localized: "pending_sync_wait")) {
                model.waitForPendingSync()
            }
            .accessibilityIdentifier("departure_pending_sync_wait")
            Button(String(localized: String.LocalizationValue(departureDiscardLabelKey(intent))), role: .destructive) {
                model.discardPendingChanges(for: intent)
            }
            .accessibilityIdentifier("departure_pending_sync_discard")
            Button(String(localized: "cancel"), role: .cancel) {
                model.dismissSessionMessage()
            }
            .accessibilityIdentifier("departure_pending_sync_cancel")
        case .confirmDeleteAccount:
            Button(String(localized: "settings_delete_account"), role: .destructive) {
                model.confirmDeleteAccount()
            }
            .accessibilityIdentifier("departure_confirm_delete_account")
            Button(String(localized: "cancel"), role: .cancel) {
                model.dismissSessionMessage()
            }
            .accessibilityIdentifier("departure_confirm_cancel")
        case .confirmDeleteLocalData:
            Button(String(localized: "settings_delete_local_data"), role: .destructive) {
                model.confirmDeleteLocalData()
            }
            .accessibilityIdentifier("departure_confirm_delete_local_data")
            Button(String(localized: "cancel"), role: .cancel) {
                model.dismissSessionMessage()
            }
            .accessibilityIdentifier("departure_confirm_cancel")
        case .failure:
            Button(String(localized: "ok"), role: .cancel) {
                model.dismissSessionMessage()
            }
            .accessibilityIdentifier("departure_failure_ok")
        }
    }
}
