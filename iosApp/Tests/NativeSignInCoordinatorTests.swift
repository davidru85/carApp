import AuthenticationServices
import Foundation
import Shared
import XCTest
@testable import carApp

/// The intent split of the one native sign-in path. Each test drives a real `SessionStateHolder` from
/// a test graph; with no deletion waiting, `startReauthentication` is a no-op in shared code, so only
/// the host-side bookkeeping is observed here. The shared outcomes are pinned by
/// `SessionDepartureLifecycleTest`.
@MainActor
final class NativeSignInCoordinatorTests: XCTestCase {
    private var graph: SwiftAppGraph!

    override func setUp() {
        super.setUp()
        graph = createSwiftAppGraph(isDebugBuild: false)
    }

    override func tearDown() {
        graph.close()
        graph = nil
        super.tearDown()
    }

    func testACancelledAppleReauthenticationEndsTheHostReauthenticationStage() {
        let coordinator = NativeSignInCoordinator(sessionStateHolder: graph.sessionStateHolder())
        var abandoned = 0
        coordinator.onReauthenticationAbandoned = { abandoned += 1 }

        coordinator.prepareAppleReauthenticationRequest(ASAuthorizationAppleIDProvider().createRequest())
        coordinator.completeAppleAuthorization(.failure(appleCancellation()))

        XCTAssertEqual(abandoned, 1)
    }

    func testACancelledPermanentAppleSignInIsNotReportedAsAReauthentication() {
        let coordinator = NativeSignInCoordinator(sessionStateHolder: graph.sessionStateHolder())
        var abandoned = 0
        coordinator.onReauthenticationAbandoned = { abandoned += 1 }

        coordinator.prepareAppleRequest(ASAuthorizationAppleIDProvider().createRequest())
        coordinator.completeAppleAuthorization(.failure(appleCancellation()))

        XCTAssertEqual(abandoned, 0)
    }

    func testAnAppleReauthenticationRequestCarriesTheHashedNonce() throws {
        let coordinator = NativeSignInCoordinator(sessionStateHolder: graph.sessionStateHolder())
        let request = ASAuthorizationAppleIDProvider().createRequest()

        coordinator.prepareAppleReauthenticationRequest(request)

        let nonce = try XCTUnwrap(request.nonce)
        XCTAssertEqual(nonce.count, 64)
        XCTAssertTrue(nonce.allSatisfy { "0123456789abcdef".contains($0) })
    }

    /// The host's "Cancel" is safe when no deletion waits: nothing stays busy and no message appears.
    func testCancellingWithoutAPendingDeletionLeavesTheSessionIdle() {
        let holder = graph.sessionStateHolder()
        let coordinator = NativeSignInCoordinator(sessionStateHolder: holder)

        coordinator.cancelReauthentication()

        XCTAssertFalse(holder.state.value.isBusy)
        XCTAssertNil(holder.state.value.message)
    }

    private func appleCancellation() -> NSError {
        NSError(domain: ASAuthorizationError.errorDomain, code: ASAuthorizationError.canceled.rawValue)
    }
}
