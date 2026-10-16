import AuthenticationServices
import CryptoKit
import FirebaseCore
import Foundation
import GoogleSignIn
import Security
import Shared
import UIKit

enum AppleSignInNonce {
    static let alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVXYZabcdefghijklmnopqrstuvwxyz-._"

    static func generate(length: Int = 32) throws -> String {
        precondition(length > 0)
        var randomBytes = [UInt8](repeating: 0, count: length)
        let status = SecRandomCopyBytes(kSecRandomDefault, randomBytes.count, &randomBytes)
        guard status == errSecSuccess else {
            throw AppleSignInNonceError.randomGenerationFailed(status)
        }
        return String(randomBytes.map { byte in
            alphabet[alphabet.index(alphabet.startIndex, offsetBy: Int(byte) % alphabet.count)]
        })
    }

    static func sha256(_ value: String) -> String {
        SHA256.hash(data: Data(value.utf8)).map { String(format: "%02x", $0) }.joined()
    }
}

private enum AppleSignInNonceError: Error {
    case randomGenerationFailed(OSStatus)
}

func nativeSignInFailure(for error: Error) -> NativeSignInFailure {
    let nsError = error as NSError
    if nsError.domain == ASAuthorizationError.errorDomain,
       nsError.code == ASAuthorizationError.canceled.rawValue {
        return .cancelled
    }
    if nsError.domain == kGIDSignInErrorDomain,
       nsError.code == GIDSignInError.canceled.rawValue {
        return .cancelled
    }
    if nsError.domain == NSURLErrorDomain {
        return .network
    }
    if let underlying = nsError.userInfo[NSUnderlyingErrorKey] as? Error {
        let mapped = nativeSignInFailure(for: underlying)
        if mapped != .unknown {
            return mapped
        }
    }
    return .unknown
}

/// Which shared intent opens a native credential attempt (`docs/CONTRACTS.md §20.10`). One native
/// picker path serves every intent, so the Google and Apple flows are never duplicated: the intent only
/// decides which `SessionStateHolder` member opens the attempt, and the completion is always
/// `completeGoogleSignIn` / `completeAppleSignIn`, which the holder routes by the attempt it opened.
/// `E4-05` adds the account-conversion intent here.
enum NativeSignInIntent: Equatable {
    case permanentSignIn
    case reauthentication
}

@MainActor
final class NativeSignInCoordinator {
    private let sessionStateHolder: SessionStateHolder
    private var rawAppleNonce: String?
    private var activeIntent: NativeSignInIntent?

    /// Called when a re-authentication attempt ends natively, before a credential reached the shared
    /// holder: the picker was cancelled, failed or was misconfigured. `failSignIn` abandons the
    /// suspended deletion in that case (`StateHolders.kt` `failSignIn`), so the host leaves its
    /// re-authentication stage. A credential that reached the holder is not reported here: its outcome
    /// arrives as published session state.
    var onReauthenticationAbandoned: (@MainActor () -> Void)?

    init(sessionStateHolder: SessionStateHolder) {
        self.sessionStateHolder = sessionStateHolder
    }

    func prepareAppleRequest(_ request: ASAuthorizationAppleIDRequest) {
        prepareApple(request, intent: .permanentSignIn)
    }

    /// The Apple request of a re-authentication. `startReauthentication(provider)` opens the attempt
    /// before the native sheet, as `docs/CONTRACTS.md §20.10` requires.
    func prepareAppleReauthenticationRequest(_ request: ASAuthorizationAppleIDRequest) {
        prepareApple(request, intent: .reauthentication)
    }

    func completeAppleAuthorization(_ result: Result<ASAuthorization, Error>) {
        defer { rawAppleNonce = nil }
        switch result {
        case .success(let authorization):
            guard
                let credential = authorization.credential as? ASAuthorizationAppleIDCredential,
                let identityToken = credential.identityToken,
                let idToken = String(data: identityToken, encoding: .utf8),
                let rawAppleNonce
            else {
                fail(reason: .configuration)
                return
            }
            activeIntent = nil
            sessionStateHolder.completeAppleSignIn(idToken: idToken, rawNonce: rawAppleNonce)
        case .failure(let error):
            fail(reason: nativeSignInFailure(for: error))
        }
    }

    func startGoogleSignIn() {
        startGoogle(intent: .permanentSignIn)
    }

    /// The Google picker of a re-authentication, opened by `startReauthentication(GOOGLE)`.
    func startGoogleReauthentication() {
        startGoogle(intent: .reauthentication)
    }

    /// The owner declined to re-authenticate. `docs/CONTRACTS.md §20.10`: a cancelled
    /// re-authentication abandons the suspended deletion without deleting anything. The shared holder
    /// expresses that cancellation only through an active re-authentication attempt
    /// (`SessionDepartureLifecycleTest.cancellingReauthenticationAbandonsTheDeletionWithoutClearingLocalData`),
    /// so the attempt is opened and cancelled at once. When no deletion is waiting,
    /// `startReauthentication` does nothing and `failSignIn(CANCELLED)` only clears the message, so the
    /// call is safe in every state.
    func cancelReauthentication() {
        activeIntent = nil
        sessionStateHolder.startReauthentication(provider: .google)
        sessionStateHolder.failSignIn(reason: .cancelled)
    }

    private func begin(_ intent: NativeSignInIntent, provider: AuthProvider) {
        activeIntent = intent
        switch intent {
        case .permanentSignIn:
            sessionStateHolder.startPermanentSignIn(provider: provider)
        case .reauthentication:
            sessionStateHolder.startReauthentication(provider: provider)
        }
    }

    private func prepareApple(_ request: ASAuthorizationAppleIDRequest, intent: NativeSignInIntent) {
        begin(intent, provider: .apple)
        do {
            let nonce = try AppleSignInNonce.generate()
            rawAppleNonce = nonce
            request.nonce = AppleSignInNonce.sha256(nonce)
        } catch {
            rawAppleNonce = nil
            fail(reason: .configuration)
        }
    }

    private func startGoogle(intent: NativeSignInIntent) {
        begin(intent, provider: .google)
        guard
            let clientID = FirebaseApp.app()?.options.clientID,
            let presentingViewController = Self.presentingViewController()
        else {
            fail(reason: .configuration)
            return
        }
        GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientID)
        GIDSignIn.sharedInstance.signIn(withPresenting: presentingViewController) { [weak self] result, error in
            MainActor.assumeIsolated {
                guard let self else { return }
                if let error {
                    self.fail(reason: nativeSignInFailure(for: error))
                    return
                }
                guard let user = result?.user, let idToken = user.idToken?.tokenString else {
                    self.fail(reason: .configuration)
                    return
                }
                self.activeIntent = nil
                self.sessionStateHolder.completeGoogleSignIn(
                    idToken: idToken,
                    accessToken: user.accessToken.tokenString
                )
            }
        }
    }

    /// Ends the native attempt without a credential and tells the host when that attempt was a
    /// re-authentication.
    private func fail(reason: NativeSignInFailure) {
        let endedIntent = activeIntent
        activeIntent = nil
        sessionStateHolder.failSignIn(reason: reason)
        if endedIntent == .reauthentication {
            onReauthenticationAbandoned?()
        }
    }

    private static func presentingViewController() -> UIViewController? {
        let rootViewController = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first(where: \.isKeyWindow)?
            .rootViewController
        return topViewController(from: rootViewController)
    }

    private static func topViewController(from viewController: UIViewController?) -> UIViewController? {
        if let navigationController = viewController as? UINavigationController {
            return topViewController(from: navigationController.visibleViewController)
        }
        if let tabBarController = viewController as? UITabBarController {
            return topViewController(from: tabBarController.selectedViewController)
        }
        if let presentedViewController = viewController?.presentedViewController {
            return topViewController(from: presentedViewController)
        }
        return viewController
    }
}
