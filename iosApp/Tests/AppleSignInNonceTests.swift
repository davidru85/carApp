import XCTest
@testable import carApp

final class AppleSignInNonceTests: XCTestCase {
    func testSHA256UsesTheLowercaseFirebaseRepresentation() {
        XCTAssertEqual(
            AppleSignInNonce.sha256("abc"),
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        )
    }

    func testGeneratedRawNonceUsesTheRequestedLengthAndAllowedAlphabet() throws {
        let nonce = try AppleSignInNonce.generate(length: 32)

        XCTAssertEqual(nonce.count, 32)
        XCTAssertTrue(nonce.allSatisfy(AppleSignInNonce.alphabet.contains))
    }
}
