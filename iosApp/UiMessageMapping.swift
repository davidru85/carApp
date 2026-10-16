import Foundation
import Shared

typealias ConsumptionInvalidReason = ModelConsumptionInvalidReason

/// The iOS half of the `docs/CONTRACTS.md §14.1` code-to-copy table (`{D-B1-D9}`).
///
/// `UiMessage.code` is a `String` (`§20.10`), so the compiler cannot prove this `switch` exhaustive.
/// Every code the shared layer can publish therefore has its own `case`, one code per case, and
/// `contract-check` assertion 41 fails when a case is missing or names a different key from the Android
/// mapping or the table (`{D-B1-D10}`). The `default` branch is the `§6` safety net only: it is
/// unreachable for every known code and renders the generic copy for a code a future shared change
/// publishes before this table learns it. Several codes share a key on purpose, because the table
/// assigns copy per user-actionable outcome rather than per leaf.
func uiMessageKey(for code: String) -> String {
    switch code {
    case "VALIDATION.REQUIRED_FIELD":
        return "error_required_field"
    case "VALIDATION.INVALID_LENGTH":
        return "error_invalid_length"
    case "VALIDATION.OUT_OF_RANGE":
        return "error_out_of_range"
    case "VALIDATION.EDIT_NOT_ALLOWED":
        return "error_edit_not_allowed"
    case "VALIDATION.DUPLICATE_NAME":
        return "error_duplicate_vehicle_name"
    case "VALIDATION.FUTURE_DATE":
        return "error_future_date"
    case "VALIDATION.INVALID_MONEY_INPUT":
        return "error_money_input"
    case "VALIDATION.NO_OP":
        return "error_no_changes"
    case "VALIDATION.INVALID_UNIT":
        return "error_currency"
    case "VALIDATION.ENTITY_DELETED":
        return "error_entity_unavailable"
    case "VALIDATION.ENTITY_NOT_FOUND":
        return "error_entity_unavailable"
    case "WARNING.ODOMETER_INCONSISTENT":
        return "odometer_warning_body"
    case "WARNING.PENDING_SYNC":
        return "pending_sync_title"
    case "AUTH.CANCELLED":
        return "error_auth_cancelled"
    case "AUTH.NETWORK_UNAVAILABLE":
        return "error_auth_network"
    case "AUTH.CREDENTIAL_ALREADY_IN_USE":
        return "error_auth_credential_in_use"
    case "AUTH.PROVIDER_UNAVAILABLE":
        return "error_auth_provider"
    case "AUTH.NO_ACCOUNT_AVAILABLE":
        return "error_auth_no_account"
    case "AUTH.TOKEN_EXPIRED":
        return "error_auth_session_expired"
    case "AUTH.PERMISSION_DENIED":
        return "error_auth_permission_denied"
    case "AUTH.REQUIRES_RECENT_LOGIN":
        return "error_auth_requires_recent_login"
    case "AUTH.UID_WOULD_CHANGE":
        return "error_auth_uid_would_change"
    case "AUTH.ACCOUNT_DELETION_REMOTE_FAILED":
        return "error_account_deletion_remote_failed"
    case "AUTH.UNKNOWN":
        return "error_unexpected"
    case "PERSISTENCE.DATABASE_UNAVAILABLE":
        return "error_persistence"
    case "PERSISTENCE.TRANSACTION_FAILED":
        return "error_persistence"
    case "PERSISTENCE.MIGRATION_FAILED":
        return "error_persistence"
    case "PERSISTENCE.SERIALIZATION_FAILED":
        return "error_persistence"
    case "PERSISTENCE.CONSTRAINT_VIOLATION":
        return "error_persistence"
    case "SYNC.RETRYABLE_NETWORK":
        return "error_backup_offline"
    case "SYNC.AUTH_EXPIRED":
        return "error_backup_session"
    case "SYNC.PERMISSION_DENIED":
        return "error_backup_rejected"
    case "SYNC.VALIDATION_REJECTED":
        return "error_backup_rejected"
    case "SYNC.PAYLOAD_POISONED":
        return "error_backup_rejected"
    case "SYNC.CONFLICT_UNRESOLVED":
        return "error_restore"
    case "SYNC.REMOTE_UNAVAILABLE":
        return "error_backup_offline"
    case "REMOTE.UNAVAILABLE":
        return "error_backup_offline"
    case "REMOTE.DEADLINE_EXCEEDED":
        return "error_backup_offline"
    case "REMOTE.PERMISSION_DENIED":
        return "error_backup_rejected"
    case "REMOTE.UNAUTHENTICATED":
        return "error_backup_session"
    case "REMOTE.INVALID_ARGUMENT":
        return "error_backup_rejected"
    case "REMOTE.NOT_FOUND":
        return "error_restore"
    case "REMOTE.UNKNOWN":
        return "error_restore"
    case "SECURITY.RULES_REJECTED":
        return "error_backup_rejected"
    case "SECURITY.OWNER_MISMATCH":
        return "error_backup_rejected"
    case "UNEXPECTED":
        return "error_unexpected"
    case "CONFIRMATION.OdometerInconsistent":
        return "odometer_warning_body"
    case "CONFIRMATION.DiscardPendingChanges":
        return "pending_sync_title"
    case "CONFIRMATION.DeleteAccount":
        return "confirm_delete_account"
    case "CONFIRMATION.DeleteLocalData":
        return "confirm_delete_local_data"
    case "CONFIRMATION.AdoptExistingAccount":
        return "confirm_replace_existing_account"
    case "INFO.CONFIRM_DELETE_VEHICLE":
        return "delete_vehicle_confirmation"
    case "INFO.CONFIRM_DELETE_FUEL_ENTRY":
        return "delete_fuel_entry_confirmation"
    default:
        return "error_unexpected"
    }
}

/// The localized copy of a `UiMessage` code: the call-site API every view uses.
func localizedUiMessage(for code: String) -> String {
    String(localized: String.LocalizationValue(uiMessageKey(for: code)))
}

/// The catalogue key that explains why a fuel entry has no consumption, or that none is available.
func consumptionExplanationKey(for reason: ConsumptionInvalidReason?) -> String {
    guard let reason = reason else {
        return "consumption_unavailable"
    }
    switch reason {
    case .noPreviousFullTank:
        return "consumption_no_previous_full_tank"
    case .endEntryNotFullTank:
        return "consumption_partial_tank"
    case .missedEntriesInSegment:
        return "consumption_missed_entries"
    case .inconsistentOdometerInSegment:
        return "consumption_inconsistent_odometer"
    case .nonPositiveDistance:
        return "consumption_non_positive_distance"
    case .duplicateOdometerInSegment:
        return "consumption_duplicate_odometer"
    }
}

func consumptionExplanation(for reason: ConsumptionInvalidReason?) -> String {
    String(localized: String.LocalizationValue(consumptionExplanationKey(for: reason)))
}

extension UiMessage {
    var localizedText: String {
        localizedUiMessage(for: code)
    }
}
