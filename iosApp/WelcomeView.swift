import AuthenticationServices
import Shared
import SwiftUI

struct WelcomeView: View {
    let state: SessionUiState
    let signInCoordinator: NativeSignInCoordinator
    let onContinueWithoutAccount: () -> Void

    var body: some View {
        // `E4-02`: `ViewThatFits` renders both branches in the hierarchy, which the accessibility audit
        // reads as duplicated, partially unsupported Dynamic Type text, so the welcome scrolls.
        ScrollView {
            content
        }
    }

    private var content: some View {
        VStack(spacing: 18) {
            Text("welcome_title")
                .font(.largeTitle.bold())
                .multilineTextAlignment(.center)
                .accessibilityAddTraits(.isHeader)
                .accessibilityIdentifier("welcome_title")
            Text("welcome_body")
                .foregroundStyle(.primary)
                .multilineTextAlignment(.center)

            SignInWithAppleButton(
                .continue,
                onRequest: signInCoordinator.prepareAppleRequest,
                onCompletion: signInCoordinator.completeAppleAuthorization
            )
            .signInWithAppleButtonStyle(.black)
            .frame(height: 50)
            .accessibilityIdentifier("welcome_apple")
            .disabled(state.isBusy)

            Button(action: signInCoordinator.startGoogleSignIn) {
                Text("continue_with_google")
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
            .tint(AccessibleColors.controlAccentOnFill)
            .accessibilityIdentifier("welcome_google")
            .disabled(state.isBusy)

            Button(action: onContinueWithoutAccount) {
                Text("continue_without_account")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)
            .controlSize(.large)
            .tint(AccessibleColors.controlAccent)
            .accessibilityIdentifier("welcome_guest")
            .disabled(state.isBusy)

            if state.isBusy {
                ProgressView()
                    .accessibilityLabel(Text("loading"))
            }
            if let message = state.message {
                Text(message.localizedText)
                    .foregroundStyle(AccessibleColors.errorText)
                    .font(.footnote)
                    .fixedSize(horizontal: false, vertical: true)
                    .announcedOnAppearance(message.localizedText)
            }
        }
        .padding(24)
    }
}
