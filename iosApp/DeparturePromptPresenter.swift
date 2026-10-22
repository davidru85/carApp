import Shared
import SwiftUI

/// The answers the departure alerts can give. Each is one `SessionStateHolder` intent (or the
/// composition of intents the contract names), so the alert renderer itself holds no model and the
/// Debug review fixtures can render it with inert callbacks (`docs/CONTRACTS.md §20.10`).
struct DeparturePromptCallbacks {
    let onWaitForPendingSync: () -> Void
    let onDiscardPendingChanges: (DepartureDiscardIntent) -> Void
    let onDismiss: () -> Void
    let onConfirmDeleteAccount: () -> Void
    let onConfirmDeleteLocalData: () -> Void
}

/// Presents the F-5 departure prompts over the signed-in surface (`D-213`, `D-215`, `D-158`).
///
/// The prompts are owned by `SessionStateHolder` and arrive as its typed `message`; this modifier only
/// decides how one is drawn (`departurePrompt(for:)`) and which intent each button calls. It is
/// attached once at the root of the signed-in surface, above the vehicle list's navigation stack, so a
/// prompt raised from the pushed Settings screen and an outcome published after the departure screen
/// handed control back are both drawn by the same presenter, with no modal between them and the owner.
///
/// The alert renderer (`DeparturePromptAlert`) is separated from the model so a review fixture can render
/// exactly the production alert with inert callbacks. There is still one live model binding here and one
/// alert renderer.
struct DeparturePromptPresenter: ViewModifier {
    @ObservedObject var model: WalkingSkeletonModel

    func body(content: Content) -> some View {
        content.modifier(DeparturePromptAlert(
            current: departurePrompt(for: model.sessionState),
            callbacks: DeparturePromptCallbacks(
                onWaitForPendingSync: model.waitForPendingSync,
                onDiscardPendingChanges: { model.discardPendingChanges(for: $0) },
                onDismiss: model.dismissSessionMessage,
                onConfirmDeleteAccount: model.confirmDeleteAccount,
                onConfirmDeleteLocalData: model.confirmDeleteLocalData
            )
        ))
    }
}

/// The alert itself: the `E4-02` review fixtures and the production modifier both render this struct.
///
/// The presented value is a local copy refreshed from the derived prompt. An alert button dismisses its
/// alert before the holder's next emission arrives, and the local copy lets the next prompt of a
/// two-step protocol (a discard, then the destructive confirmation) present as a new alert.
struct DeparturePromptAlert: ViewModifier {
    let current: DeparturePrompt?
    let callbacks: DeparturePromptCallbacks
    @State private var presented: DeparturePrompt?

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
            // F-5: wait for sync, cancel, or discard after a destructive confirmation (`D-213`).
            Button(String(localized: "pending_sync_wait")) {
                callbacks.onWaitForPendingSync()
            }
            .accessibilityIdentifier("departure_pending_sync_wait")
            Button(String(localized: String.LocalizationValue(departureDiscardLabelKey(intent))), role: .destructive) {
                callbacks.onDiscardPendingChanges(intent)
            }
            .accessibilityIdentifier("departure_pending_sync_discard")
            Button(String(localized: "cancel"), role: .cancel) {
                callbacks.onDismiss()
            }
            .accessibilityIdentifier("departure_pending_sync_cancel")
        case .confirmDeleteAccount:
            Button(String(localized: "settings_delete_account"), role: .destructive) {
                callbacks.onConfirmDeleteAccount()
            }
            .accessibilityIdentifier("departure_confirm_delete_account")
            Button(String(localized: "cancel"), role: .cancel) {
                callbacks.onDismiss()
            }
            .accessibilityIdentifier("departure_confirm_cancel")
        case .confirmDeleteLocalData:
            Button(String(localized: "settings_delete_local_data"), role: .destructive) {
                callbacks.onConfirmDeleteLocalData()
            }
            .accessibilityIdentifier("departure_confirm_delete_local_data")
            Button(String(localized: "cancel"), role: .cancel) {
                callbacks.onDismiss()
            }
            .accessibilityIdentifier("departure_confirm_cancel")
        case .failure:
            Button(String(localized: "ok"), role: .cancel) {
                callbacks.onDismiss()
            }
            .accessibilityIdentifier("departure_failure_ok")
        }
    }
}
