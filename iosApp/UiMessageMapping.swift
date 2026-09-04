import Foundation
import Shared

typealias ConsumptionInvalidReason = ModelConsumptionInvalidReason

func localizedUiMessage(for code: String) -> String {
    let key: String
    switch code {
    case "VALIDATION.REQUIRED_FIELD":
        key = "error_required_field"
    case "VALIDATION.INVALID_LENGTH":
        key = "error_invalid_length"
    case "VALIDATION.OUT_OF_RANGE":
        key = "error_out_of_range"
    case "VALIDATION.EDIT_NOT_ALLOWED":
        key = "error_edit_not_allowed"
    case "VALIDATION.DUPLICATE_NAME":
        key = "error_duplicate_vehicle_name"
    case "VALIDATION.NO_OP":
        key = "error_no_changes"
    case "VALIDATION.ENTITY_DELETED":
        key = "error_vehicle_deleted"
    case "VALIDATION.ENTITY_NOT_FOUND":
        key = "vehicle_not_found"
    case "VALIDATION.FUTURE_DATE":
        key = "error_future_date"
    case "VALIDATION.INVALID_MONEY_INPUT":
        key = "error_money_input"
    case "VALIDATION.INVALID_UNIT":
        key = "error_currency"
    case "PERSISTENCE.DATABASE_UNAVAILABLE",
         "PERSISTENCE.TRANSACTION_FAILED",
         "PERSISTENCE.MIGRATION_FAILED",
         "PERSISTENCE.SERIALIZATION_FAILED",
         "PERSISTENCE.CONSTRAINT_VIOLATION":
        key = "error_persistence"
    case "REMOTE.UNAVAILABLE",
         "REMOTE.DEADLINE_EXCEEDED",
         "REMOTE.PERMISSION_DENIED",
         "REMOTE.UNAUTHENTICATED",
         "REMOTE.INVALID_ARGUMENT",
         "REMOTE.NOT_FOUND",
         "REMOTE.UNKNOWN":
        key = "error_restore"
    case "AUTH.CANCELLED":
        key = "error_auth_cancelled"
    case "AUTH.NETWORK_UNAVAILABLE":
        key = "error_auth_network"
    case "AUTH.PROVIDER_UNAVAILABLE":
        key = "error_auth_provider"
    default:
        key = "error_unexpected"
    }
    return String(localized: String.LocalizationValue(key))
}

func consumptionExplanation(for reason: ConsumptionInvalidReason?) -> String {
    guard let reason = reason else {
        return String(localized: "consumption_unavailable")
    }
    let key: String
    switch reason {
    case .noPreviousFullTank:
        key = "consumption_no_previous_full_tank"
    case .endEntryNotFullTank:
        key = "consumption_partial_tank"
    case .missedEntriesInSegment:
        key = "consumption_missed_entries"
    case .inconsistentOdometerInSegment:
        key = "consumption_inconsistent_odometer"
    case .nonPositiveDistance:
        key = "consumption_non_positive_distance"
    case .duplicateOdometerInSegment:
        key = "consumption_duplicate_odometer"
    }
    return String(localized: String.LocalizationValue(key))
}

extension UiMessage {
    var localizedText: String {
        localizedUiMessage(for: code)
    }
}
