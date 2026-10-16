import AuthenticationServices
import Shared
import SwiftUI

/// `E4-05`: the F-4 entry point is offered only to an anonymous session. `startAccountConversion`
/// refuses every other session (`docs/CONTRACTS.md §20.10`), and `D-224` keeps the row hidden
/// while the session is still `LOCAL`, until adoption makes it anonymous.
func showsLinkAccountRow(phase: SessionPhase) -> Bool {
    switch phase {
    case .anonymous:
        return true
    case .unknown, .local, .permanent, .signedOut, .deleting:
        return false
    }
}

/// The one session confirmation the conversion surface answers itself (`docs/CONTRACTS.md §11.3`).
func isAccountConversionConfirmation(_ confirmation: Confirmation?) -> Bool {
    confirmation == .adoptExistingAccount
}

/// The session-channel key for `code`, or `nil` to keep the shared mapping. On that channel a
/// `REMOTE.*` code is published only by a confirmed account replacement that failed after the session
/// switch, which the app resumes on its own; departures publish only `AUTH.*` and `PERSISTENCE.*`.
func sessionMessageKey(for code: String) -> String? {
    code.hasPrefix("REMOTE.") ? "error_conversion_replacement_pending" : nil
}

/// Copy for a message of the session channel.
func localizedSessionMessage(_ message: UiMessage) -> String {
    localizedSessionMessage(forCode: message.code)
}

/// The code-based form the departure presenter uses, which transports only the code.
func localizedSessionMessage(forCode code: String) -> String {
    guard let key = sessionMessageKey(for: code) else {
        return localizedUiMessage(for: code)
    }
    return String(localized: String.LocalizationValue(key))
}

/// The anonymous-only "link account" rows: an explanation, then Apple and Google (F-1: iOS offers
/// Apple whenever it offers Google). Each control is its own row, so a list row never holds two
/// buttons. The rows hold no conversion logic: the coordinator starts with
/// `SessionStateHolder.startAccountConversion` and completes through the existing native pickers.
struct LinkAccountRows: View {
    let state: SessionUiState
    let signInCoordinator: NativeSignInCoordinator

    var body: some View {
        if showsLinkAccountRow(phase: state.phase) {
            VStack(alignment: .leading, spacing: 4) {
                Text("settings_link_account_title")
                    .font(.body.weight(.semibold))
                    .accessibilityAddTraits(.isHeader)
                Text("settings_link_account_body")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                if state.isBusy {
                    ProgressView()
                        .accessibilityLabel(Text("settings_link_account_in_progress"))
                        .accessibilityIdentifier("settings_link_account_progress")
                }
            }
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("settings_link_account_row")

            SignInWithAppleButton(
                .continue,
                onRequest: signInCoordinator.prepareAppleConversionRequest,
                onCompletion: signInCoordinator.completeAppleAuthorization
            )
            .signInWithAppleButtonStyle(.black)
            .frame(height: 50)
            .accessibilityIdentifier("settings_link_account_apple")
            .disabled(state.isBusy)

            Button(action: signInCoordinator.startGoogleConversion) {
                Text("continue_with_google")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)
            .controlSize(.large)
            .accessibilityIdentifier("settings_link_account_google")
            .disabled(state.isBusy)
        }
    }
}

private let accountConversionConfirmationCode = "CONFIRMATION.AdoptExistingAccount"

/// The destructive credential-collision confirmation of `docs/CONTRACTS.md §11.3`. Its presentation
/// is local state that follows the published confirmation, so dismissing the alert never runs an
/// intent of its own: only the two buttons do.
private struct ConversionCollisionAlert: ViewModifier {
    let confirmation: Confirmation?
    let onConfirm: () -> Void
    let onCancel: () -> Void
    @State private var isPresented = false

    func body(content: Content) -> some View {
        content
            .onAppear { isPresented = isAccountConversionConfirmation(confirmation) }
            .onChange(of: isAccountConversionConfirmation(confirmation)) { presented in
                isPresented = presented
            }
            .alert(String(localized: "conversion_collision_title"), isPresented: $isPresented) {
                Button(String(localized: "cancel"), role: .cancel) {
                    onCancel()
                }
                .accessibilityIdentifier("settings_conversion_cancel")

                Button(String(localized: "conversion_collision_confirm"), role: .destructive) {
                    onConfirm()
                }
                .accessibilityIdentifier("settings_conversion_confirm")
            } message: {
                Text(localizedUiMessage(for: accountConversionConfirmationCode))
            }
    }
}

extension View {
    func conversionCollisionAlert(
        confirmation: Confirmation?,
        onConfirm: @escaping () -> Void,
        onCancel: @escaping () -> Void
    ) -> some View {
        modifier(ConversionCollisionAlert(confirmation: confirmation, onConfirm: onConfirm, onCancel: onCancel))
    }
}
