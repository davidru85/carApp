import AuthenticationServices
import Foundation
import GoogleSignIn
import Shared
import XCTest
@testable import carApp

final class NativeSignInFailureMappingTests: XCTestCase {
    func testAppleCancellationMapsWithoutFreeText() {
        let error = NSError(
            domain: ASAuthorizationError.errorDomain,
            code: ASAuthorizationError.canceled.rawValue
        )

        XCTAssertEqual(nativeSignInFailure(for: error), .cancelled)
    }

    func testGoogleCancellationMapsWithoutFreeText() {
        let error = NSError(domain: kGIDSignInErrorDomain, code: GIDSignInError.canceled.rawValue)

        XCTAssertEqual(nativeSignInFailure(for: error), .cancelled)
    }

    func testNetworkFailureMapsWithoutFreeText() {
        let error = NSError(domain: NSURLErrorDomain, code: NSURLErrorNotConnectedToInternet)

        XCTAssertEqual(nativeSignInFailure(for: error), .network)
    }

    func testUnknownFailureMapsWithoutFreeText() {
        let error = NSError(domain: "test", code: 1)

        XCTAssertEqual(nativeSignInFailure(for: error), .unknown)
    }
}
