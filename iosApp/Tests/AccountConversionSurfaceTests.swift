import Shared
import XCTest
@testable import carApp

final class AccountConversionSurfaceTests: XCTestCase {
    private let remoteCodes = [
        "REMOTE.UNAVAILABLE",
        "REMOTE.DEADLINE_EXCEEDED",
        "REMOTE.PERMISSION_DENIED",
        "REMOTE.UNAUTHENTICATED",
        "REMOTE.INVALID_ARGUMENT",
        "REMOTE.NOT_FOUND",
        "REMOTE.UNKNOWN",
    ]

    private let conversionKeys = [
        "settings_link_account_title",
        "settings_link_account_body",
        "settings_link_account_in_progress",
        "conversion_collision_title",
        "conversion_collision_confirm",
        "confirm_replace_existing_account",
        "error_auth_uid_would_change",
        "error_auth_credential_in_use",
        "error_auth_permission_denied",
        "error_conversion_replacement_pending",
    ]

    func testAnAnonymousSessionShowsTheLinkAccountRow() {
        XCTAssertTrue(showsLinkAccountRow(phase: .anonymous))
    }

    func testEveryOtherSessionPhaseHidesTheLinkAccountRow() {
        for phase in [SessionPhase.unknown, .local, .permanent, .signedOut, .deleting] {
            XCTAssertFalse(showsLinkAccountRow(phase: phase), "\(phase) must not offer account linking")
        }
    }

    /// `D-224`: a local session has no anonymous identity to link until adoption creates one.
    func testALocalSessionHidesTheLinkAccountRowUntilAdoptionMakesItAnonymous() {
        XCTAssertFalse(showsLinkAccountRow(phase: .local))
    }

    func testTheAdoptExistingAccountConfirmationOpensTheCollisionAlert() {
        XCTAssertTrue(isAccountConversionConfirmation(.adoptExistingAccount))
    }

    func testNoOtherConfirmationOpensTheCollisionAlert() {
        XCTAssertFalse(isAccountConversionConfirmation(nil))
        for confirmation in [Confirmation.odometerInconsistent, .discardPendingChanges, .deleteAccount, .deleteLocalData] {
            XCTAssertFalse(isAccountConversionConfirmation(confirmation), "\(confirmation) is not the collision")
        }
    }

    func testARemoteFailureOnTheSessionChannelSaysTheReplacementKeepsBeingRetried() {
        for code in remoteCodes {
            XCTAssertEqual(sessionMessageKey(for: code), "error_conversion_replacement_pending", code)
        }
    }

    func testAnAuthFailureOnTheSessionChannelKeepsTheSharedMapping() {
        XCTAssertNil(sessionMessageKey(for: "AUTH.NETWORK_UNAVAILABLE"))
    }

    func testAUidChangeRefusalHasItsOwnMessage() {
        XCTAssertEqual(
            localizedUiMessage(for: "AUTH.UID_WOULD_CHANGE"),
            String(localized: "error_auth_uid_would_change")
        )
    }

    func testACredentialAlreadyInUseFailureHasItsOwnMessage() {
        XCTAssertEqual(
            localizedUiMessage(for: "AUTH.CREDENTIAL_ALREADY_IN_USE"),
            String(localized: "error_auth_credential_in_use")
        )
    }

    func testADeniedAccountOperationHasItsOwnMessage() {
        XCTAssertEqual(
            localizedUiMessage(for: "AUTH.PERMISSION_DENIED"),
            String(localized: "error_auth_permission_denied")
        )
    }

    func testAnUnknownAuthFailureUsesTheGenericMessage() {
        XCTAssertEqual(localizedUiMessage(for: "AUTH.UNKNOWN"), String(localized: "error_unexpected"))
    }

    /// Each bundle is read directly, so the result does not depend on the simulator's language.
    func testEveryConversionKeyHasEnglishAndSpanishCopy() {
        for language in ["en", "es"] {
            for key in conversionKeys {
                let value = localizedCopy(key, language: language)
                XCTAssertFalse(value.isEmpty, "\(key) must exist in \(language)")
                XCTAssertNotEqual(value, key, "\(key) must resolve to display copy in \(language)")
            }
        }
    }

    private func localizedCopy(_ key: String, language: String) -> String {
        for candidate in [Bundle.main, Bundle(for: type(of: self))] {
            if
                let path = candidate.path(forResource: language, ofType: "lproj"),
                let bundle = Bundle(path: path)
            {
                return bundle.localizedString(forKey: key, value: nil, table: nil)
            }
        }
        XCTFail("Missing \(language).lproj for \(key)")
        return ""
    }
}
