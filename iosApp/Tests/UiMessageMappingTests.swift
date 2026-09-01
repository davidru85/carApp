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
