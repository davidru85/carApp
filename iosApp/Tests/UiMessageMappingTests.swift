import XCTest
import Shared
@testable import carApp

final class UiMessageMappingTests: XCTestCase {
    func testValidationErrorCodeMapping() {
        let codes = [
            "VALIDATION.REQUIRED_FIELD",
            "VALIDATION.INVALID_LENGTH",
            "VALIDATION.OUT_OF_RANGE",
            "VALIDATION.EDIT_NOT_ALLOWED",
            "VALIDATION.DUPLICATE_NAME",
            "VALIDATION.NO_OP",
            "VALIDATION.ENTITY_DELETED",
            "VALIDATION.ENTITY_NOT_FOUND",
            "VALIDATION.FUTURE_DATE",
            "VALIDATION.INVALID_MONEY_INPUT",
            "VALIDATION.INVALID_UNIT",
            "PERSISTENCE.TRANSACTION_FAILED",
            "REMOTE.UNAVAILABLE",
            "UNKNOWN_CODE_SHOULD_FALLBACK",
        ]

        for code in codes {
            let message = localizedUiMessage(for: code)
            XCTAssertFalse(message.isEmpty, "Message for \(code) should not be empty")
        }
    }

    func testDeviceWithoutAnAvailableAccountGetsItsOwnMessage() {
        let message = localizedUiMessage(for: "AUTH.NO_ACCOUNT_AVAILABLE")

        XCTAssertEqual(
            message,
            String(localized: "error_auth_no_account"),
            "A device with no account to offer needs the actionable message, not the generic one."
        )
    }

    func testAccountCollisionConfirmationExplainsTheDestructiveReplacement() {
        let message = localizedUiMessage(for: "CONFIRMATION.AdoptExistingAccount")

        XCTAssertEqual(message, String(localized: "confirm_replace_existing_account"))

        // The copy MUST state both the replacement and its irreversibility in every shipped
        // language. Each bundle is read directly, because asserting the running locale's copy
        // would make the test pass or fail on the simulator's language rather than on the copy.
        let required = [
            "en": ["permanently replaces", "cannot be undone"],
            "es": ["sustituye permanentemente", "No se puede deshacer"],
        ]

        for (language, phrases) in required {
            let copy = localizedCopy("confirm_replace_existing_account", language: language)
            for phrase in phrases {
                XCTAssertTrue(copy.contains(phrase), "The \(language) copy must contain \"\(phrase)\".")
            }
        }
    }

    func testDepartureConfirmationsHaveTheirOwnDestructiveCopy() {
        let deleteAccount = localizedUiMessage(for: "CONFIRMATION.DeleteAccount")
        let deleteLocalData = localizedUiMessage(for: "CONFIRMATION.DeleteLocalData")

        XCTAssertEqual(deleteAccount, String(localized: "confirm_delete_account"))
        XCTAssertEqual(deleteLocalData, String(localized: "confirm_delete_local_data"))
        // Deleting an account and clearing this device are different consequences and must not
        // share copy, which is why they no longer share a confirmation either.
        XCTAssertNotEqual(deleteAccount, deleteLocalData)

        let required = [
            "en": ["cannot be undone", "cannot be undone"],
            "es": ["No se puede deshacer", "no se puede deshacer"],
        ]
        for (language, phrases) in required {
            XCTAssertTrue(localizedCopy("confirm_delete_account", language: language).contains(phrases[0]))
            XCTAssertTrue(localizedCopy("confirm_delete_local_data", language: language).contains(phrases[1]))
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

    func testConsumptionInvalidReasonExplanation() {
        let reasons: [ConsumptionInvalidReason?] = [
            .noPreviousFullTank,
            .endEntryNotFullTank,
            .missedEntriesInSegment,
            .inconsistentOdometerInSegment,
            .nonPositiveDistance,
            .duplicateOdometerInSegment,
            nil,
        ]

        for reason in reasons {
            let explanation = consumptionExplanation(for: reason)
            XCTAssertFalse(explanation.isEmpty, "Explanation for \(String(describing: reason)) should not be empty")
        }
    }
}
