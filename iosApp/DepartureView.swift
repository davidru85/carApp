import AuthenticationServices
import Shared
import SwiftUI

/// The `D-214` departure screen, mounted at the root in place of the signed-in surface.
///
/// The root routes here while a deletion runs (`DELETING`), whenever the session holder publishes a
/// `pendingDepartureRetry`, and while a permanent deletion waits for re-authentication. Routing at the
/// root keeps the progress, the typed failure and the retry reachable: the signed-in surface, Settings
/// included, is unmounted for the whole departure, and the phase a failure leaves behind (`DELETING`, or
/// `UNKNOWN` after the remote handover) used to route to a bare indicator that hid both
/// (`docs/CONTRACTS.md §11.5`, `§20.10`).
///
/// The view holds no rule. `departureStage` decides what is drawn from published state, and every
/// button calls one `SessionStateHolder` intent or the shared native sign-in path through the process
/// model.
struct DepartureView: View {
    let state: SessionUiState
    let isReauthenticating: Bool
    let onRetry: () -> Void
    let onDismissMessage: () -> Void
    let onReauthenticateWithGoogle: () -> Void
    let onPrepareAppleReauthentication: (ASAuthorizationAppleIDRequest) -> Void
    let onCompleteAppleAuthorization: (Result<ASAuthorization, Error>) -> Void
    let onCancelReauthentication: () -> Void

    private var stage: DepartureStage {
        departureStage(
            phase: state.phase,
            isBusy: state.isBusy,
            messageCode: state.message?.code,
            pendingDepartureRetry: state.pendingDepartureRetry,
            isReauthenticating: isReauthenticating
        )
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 18) {
                switch stage {
                case .inProgress:
                    progress
                case let .failed(code, retry):
                    failure(code: code, retry: retry)
                case let .reauthenticationRequired(failureCode):
                    reauthentication(failureCode: failureCode)
                }
            }
            .padding(24)
            .frame(maxWidth: .infinity)
        }
        .accessibilityIdentifier("departure_screen")
    }

    private var progress: some View {
        VStack(spacing: 12) {
            ProgressView()
                .accessibilityLabel(Text("loading"))
                .accessibilityIdentifier("departure_progress")
            Text("departure_in_progress")
                .multilineTextAlignment(.center)
        }
    }

    @ViewBuilder
    private func failure(code: String?, retry: DepartureRetry?) -> some View {
        Text("departure_failure_title")
            .font(.headline)
            .multilineTextAlignment(.center)
        if let code {
            Text(localizedUiMessage(for: code))
                .multilineTextAlignment(.center)
                .accessibilityIdentifier("departure_failure")
        }
        if let retry {
            // The retry repeats exactly the published step and never a `D-23` deletion that already
            // succeeded (`D-159`).
            Button(action: onRetry) {
                Text(LocalizedStringKey(departureRetryLabelKey(retry)))
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
            .tint(AccessibleColors.controlAccentOnFill)
            .accessibilityIdentifier("departure_retry")
        } else {
            Button(action: onDismissMessage) {
                Text("ok")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)
            .controlSize(.large)
            .accessibilityIdentifier("departure_dismiss")
        }
    }

    /// `§11.5` step 1. The providers stay mounted, and disabled, while an attempt is in flight, so the
    /// native Apple button can deliver its completion.
    @ViewBuilder
    private func reauthentication(failureCode: String?) -> some View {
        let providers = reauthenticationProviders(state.providers)
        Text("reauth_title")
            .font(.headline)
            .multilineTextAlignment(.center)
        Text(localizedUiMessage(for: requiresRecentLoginCode))
            .multilineTextAlignment(.center)
            .accessibilityIdentifier("departure_reauth_message")
        if let failureCode {
            let failureText = localizedUiMessage(for: failureCode)
            Text(failureText)
                .font(.footnote)
                .foregroundStyle(AccessibleColors.errorText)
                .announcedOnAppearance(failureText)
                .multilineTextAlignment(.center)
                .accessibilityIdentifier("departure_failure")
        }
        if providers.contains(.apple) {
            SignInWithAppleButton(
                .continue,
                onRequest: onPrepareAppleReauthentication,
                onCompletion: onCompleteAppleAuthorization
            )
            .signInWithAppleButtonStyle(.black)
            .frame(height: 50)
            .accessibilityIdentifier("departure_reauth_apple")
            .disabled(state.isBusy)
        }
        if providers.contains(.google) {
            // `E4-02` (`D-223`): a prominent button's system fill/label pair fails WCAG AA, so the app
            // sets both sides, exactly as the welcome screen's Google button does.
            Button(action: onReauthenticateWithGoogle) {
                Text("continue_with_google")
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
            .tint(AccessibleColors.controlAccentOnFill)
            .accessibilityIdentifier("departure_reauth_google")
            .disabled(state.isBusy)
        }
        Button(action: onCancelReauthentication) {
            Text("cancel")
                .foregroundStyle(AccessibleColors.controlAccent)
                .frame(maxWidth: .infinity)
        }
        .buttonStyle(.bordered)
        .controlSize(.large)
        .accessibilityIdentifier("departure_reauth_cancel")
        .disabled(state.isBusy)
        if state.isBusy {
            ProgressView()
                .accessibilityLabel(Text("loading"))
                .accessibilityIdentifier("departure_progress")
        }
    }
}
