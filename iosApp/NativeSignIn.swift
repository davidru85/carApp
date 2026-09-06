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

@MainActor
final class NativeSignInCoordinator {
    private let sessionStateHolder: SessionStateHolder
    private var rawAppleNonce: String?

    init(sessionStateHolder: SessionStateHolder) {
        self.sessionStateHolder = sessionStateHolder
    }

    func prepareAppleRequest(_ request: ASAuthorizationAppleIDRequest) {
        sessionStateHolder.startPermanentSignIn(provider: .apple)
        do {
            let nonce = try AppleSignInNonce.generate()
            rawAppleNonce = nonce
            request.nonce = AppleSignInNonce.sha256(nonce)
        } catch {
            rawAppleNonce = nil
            sessionStateHolder.failSignIn(reason: .configuration)
        }
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
                sessionStateHolder.failSignIn(reason: .configuration)
                return
            }
            sessionStateHolder.completeAppleSignIn(idToken: idToken, rawNonce: rawAppleNonce)
        case .failure(let error):
            sessionStateHolder.failSignIn(reason: nativeSignInFailure(for: error))
        }
    }

    func startGoogleSignIn() {
        sessionStateHolder.startPermanentSignIn(provider: .google)
        guard
            let clientID = FirebaseApp.app()?.options.clientID,
            let presentingViewController = Self.presentingViewController()
        else {
            sessionStateHolder.failSignIn(reason: .configuration)
            return
        }
        GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientID)
        GIDSignIn.sharedInstance.signIn(withPresenting: presentingViewController) { [weak self] result, error in
            MainActor.assumeIsolated {
                guard let self else { return }
                if let error {
                    self.sessionStateHolder.failSignIn(reason: nativeSignInFailure(for: error))
                    return
                }
                guard let user = result?.user, let idToken = user.idToken?.tokenString else {
                    self.sessionStateHolder.failSignIn(reason: .configuration)
                    return
                }
                self.sessionStateHolder.completeGoogleSignIn(
                    idToken: idToken,
                    accessToken: user.accessToken.tokenString
                )
            }
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
