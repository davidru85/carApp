import AuthenticationServices
import Shared
import SwiftUI

struct WelcomeView: View {
    let state: SessionUiState
    let signInCoordinator: NativeSignInCoordinator
    let onContinueWithoutAccount: () -> Void

    var body: some View {
        VStack(spacing: 18) {
            Text("welcome_title")
                .font(.largeTitle.bold())
                .multilineTextAlignment(.center)
            Text("welcome_body")
                .foregroundStyle(.secondary)
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
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
            .accessibilityIdentifier("welcome_google")
            .disabled(state.isBusy)

            Button(action: onContinueWithoutAccount) {
                Text("continue_without_account")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)
            .controlSize(.large)
            .accessibilityIdentifier("welcome_guest")
            .disabled(state.isBusy)

            if state.isBusy {
                ProgressView()
            }
            if let message = state.message {
                Text(message.localizedText)
                    .foregroundStyle(.red)
                    .font(.caption)
            }
        }
        .padding(24)
    }
}
