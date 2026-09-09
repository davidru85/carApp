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
        XCTAssertTrue(message.contains("permanently replaces"))
        XCTAssertTrue(message.contains("cannot be undone"))
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
