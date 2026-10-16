package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.common.UiMessage

/**
 * The string resource that renders this message on Android. Every host screen calls this, so no screen
 * carries a partial mapping of its own; it delegates to [uiMessageStringResource].
 */
internal fun UiMessage.stringResource(): Int = uiMessageStringResource(code)

/**
 * The Android half of the `docs/CONTRACTS.md §14.1` code-to-copy table (`D-219`).
 *
 * `UiMessage.code` is a `String` (`§20.10`), so the compiler cannot prove this `when` exhaustive. Every
 * code the shared layer can publish therefore has its own explicit branch, one code per branch, and
 * `contract-check` assertion 41 fails when a branch is missing or names a different key from the iOS
 * mapping or the table (`D-220`). The `else` branch is the `§6` safety net only: it is unreachable
 * for every known code and renders the generic copy for a code a future shared change publishes before
 * this table learns it.
 *
 * Several codes share a key on purpose, because the table assigns copy per user-actionable outcome
 * rather than per leaf. The branches are a flat lookup with one decision, so the cyclomatic-complexity
 * rule, which counts every branch, is suppressed here.
 */
@Suppress("CyclomaticComplexMethod")
internal fun uiMessageStringResource(code: String): Int =
    when (code) {
        "VALIDATION.REQUIRED_FIELD" -> R.string.error_required_field
        "VALIDATION.INVALID_LENGTH" -> R.string.error_invalid_length
        "VALIDATION.OUT_OF_RANGE" -> R.string.error_out_of_range
        "VALIDATION.EDIT_NOT_ALLOWED" -> R.string.error_edit_not_allowed
        "VALIDATION.DUPLICATE_NAME" -> R.string.error_duplicate_vehicle_name
        "VALIDATION.FUTURE_DATE" -> R.string.error_future_date
        "VALIDATION.INVALID_MONEY_INPUT" -> R.string.error_money_input
        "VALIDATION.NO_OP" -> R.string.error_no_changes
        "VALIDATION.INVALID_UNIT" -> R.string.error_currency
        "VALIDATION.ENTITY_DELETED" -> R.string.error_entity_unavailable
        "VALIDATION.ENTITY_NOT_FOUND" -> R.string.error_entity_unavailable
        "WARNING.ODOMETER_INCONSISTENT" -> R.string.odometer_warning_body
        "WARNING.PENDING_SYNC" -> R.string.pending_sync_title
        "AUTH.CANCELLED" -> R.string.error_auth_cancelled
        "AUTH.NETWORK_UNAVAILABLE" -> R.string.error_auth_network
        "AUTH.CREDENTIAL_ALREADY_IN_USE" -> R.string.error_auth_credential_in_use
        "AUTH.PROVIDER_UNAVAILABLE" -> R.string.error_auth_provider
        "AUTH.NO_ACCOUNT_AVAILABLE" -> R.string.error_auth_no_account
        "AUTH.TOKEN_EXPIRED" -> R.string.error_auth_session_expired
        "AUTH.PERMISSION_DENIED" -> R.string.error_auth_permission_denied
        "AUTH.REQUIRES_RECENT_LOGIN" -> R.string.error_auth_requires_recent_login
        "AUTH.UID_WOULD_CHANGE" -> R.string.error_auth_uid_would_change
        "AUTH.ACCOUNT_DELETION_REMOTE_FAILED" -> R.string.error_account_deletion_remote_failed
        "AUTH.UNKNOWN" -> R.string.error_unexpected
        "PERSISTENCE.DATABASE_UNAVAILABLE" -> R.string.error_persistence
        "PERSISTENCE.TRANSACTION_FAILED" -> R.string.error_persistence
        "PERSISTENCE.MIGRATION_FAILED" -> R.string.error_persistence
        "PERSISTENCE.SERIALIZATION_FAILED" -> R.string.error_persistence
        "PERSISTENCE.CONSTRAINT_VIOLATION" -> R.string.error_persistence
        "SYNC.RETRYABLE_NETWORK" -> R.string.error_backup_offline
        "SYNC.AUTH_EXPIRED" -> R.string.error_backup_session
        "SYNC.PERMISSION_DENIED" -> R.string.error_backup_rejected
        "SYNC.VALIDATION_REJECTED" -> R.string.error_backup_rejected
        "SYNC.PAYLOAD_POISONED" -> R.string.error_backup_rejected
        "SYNC.CONFLICT_UNRESOLVED" -> R.string.error_restore
        "SYNC.REMOTE_UNAVAILABLE" -> R.string.error_backup_offline
        "REMOTE.UNAVAILABLE" -> R.string.error_backup_offline
        "REMOTE.DEADLINE_EXCEEDED" -> R.string.error_backup_offline
        "REMOTE.PERMISSION_DENIED" -> R.string.error_backup_rejected
        "REMOTE.UNAUTHENTICATED" -> R.string.error_backup_session
        "REMOTE.INVALID_ARGUMENT" -> R.string.error_backup_rejected
        "REMOTE.NOT_FOUND" -> R.string.error_restore
        "REMOTE.UNKNOWN" -> R.string.error_restore
        "SECURITY.RULES_REJECTED" -> R.string.error_backup_rejected
        "SECURITY.OWNER_MISMATCH" -> R.string.error_backup_rejected
        "UNEXPECTED" -> R.string.error_unexpected
        "CONFIRMATION.OdometerInconsistent" -> R.string.odometer_warning_body
        "CONFIRMATION.DiscardPendingChanges" -> R.string.pending_sync_title
        "CONFIRMATION.DeleteAccount" -> R.string.confirm_delete_account
        "CONFIRMATION.DeleteLocalData" -> R.string.confirm_delete_local_data
        "CONFIRMATION.AdoptExistingAccount" -> R.string.confirm_replace_existing_account
        "INFO.CONFIRM_DELETE_VEHICLE" -> R.string.delete_vehicle_confirmation
        "INFO.CONFIRM_DELETE_FUEL_ENTRY" -> R.string.delete_fuel_entry_confirmation
        else -> R.string.error_unexpected
    }
