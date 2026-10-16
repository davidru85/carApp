package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.common.UiMessage
import com.ruizurraca.carapp.core.common.UiMessageKind
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The `docs/CONTRACTS.md §14.1` code-to-copy table on Android (`{D-B1-D9}`).
 *
 * [UI_MESSAGE_TABLE] is the test-side copy of the table. `contract-check` assertion 41 fails when it
 * stops naming a code the shared layer can publish, so these tests cannot fall behind the mapping
 * they pin.
 */
class UiMessageStringsTest {
    @Test
    fun everyCodeMapsToTheKeyTheTableAssigns() {
        val mismatches = UI_MESSAGE_TABLE.filter { (code, resource) -> uiMessageStringResource(code) != resource }.keys

        assertEquals(emptySet(), mismatches, "These codes do not render the copy docs/CONTRACTS.md §14.1 assigns.")
    }

    @Test
    fun onlyTheUnclassifiedFailuresRenderTheGenericCopy() {
        val generic =
            UI_MESSAGE_TABLE.keys.filter { code ->
                uiMessageStringResource(code) == R.string.error_unexpected
            }

        assertEquals(
            setOf("AUTH.UNKNOWN", "UNEXPECTED"),
            generic.toSet(),
            "Every other code tells the owner what to do; the generic copy is reserved for unclassified failures.",
        )
    }

    @Test
    fun aCodeNoTableEntryNamesFallsBackToTheGenericCopy() {
        assertEquals(R.string.error_unexpected, uiMessageStringResource("INFO.NOT_IN_THE_TABLE"))
    }

    @Test
    fun theCallSiteApiRendersTheTableCopy() {
        val message =
            UiMessage(id = 1L, kind = UiMessageKind.ERROR, code = "REMOTE.UNAVAILABLE", confirmation = null)

        assertEquals(R.string.error_backup_offline, message.stringResource())
    }
}

private val UI_MESSAGE_TABLE: Map<String, Int> =
    mapOf(
        "VALIDATION.REQUIRED_FIELD" to R.string.error_required_field,
        "VALIDATION.INVALID_LENGTH" to R.string.error_invalid_length,
        "VALIDATION.OUT_OF_RANGE" to R.string.error_out_of_range,
        "VALIDATION.EDIT_NOT_ALLOWED" to R.string.error_edit_not_allowed,
        "VALIDATION.DUPLICATE_NAME" to R.string.error_duplicate_vehicle_name,
        "VALIDATION.FUTURE_DATE" to R.string.error_future_date,
        "VALIDATION.INVALID_MONEY_INPUT" to R.string.error_money_input,
        "VALIDATION.NO_OP" to R.string.error_no_changes,
        "VALIDATION.INVALID_UNIT" to R.string.error_currency,
        "VALIDATION.ENTITY_DELETED" to R.string.error_entity_unavailable,
        "VALIDATION.ENTITY_NOT_FOUND" to R.string.error_entity_unavailable,
        "WARNING.ODOMETER_INCONSISTENT" to R.string.odometer_warning_body,
        "WARNING.PENDING_SYNC" to R.string.pending_sync_title,
        "AUTH.CANCELLED" to R.string.error_auth_cancelled,
        "AUTH.NETWORK_UNAVAILABLE" to R.string.error_auth_network,
        "AUTH.CREDENTIAL_ALREADY_IN_USE" to R.string.error_auth_credential_in_use,
        "AUTH.PROVIDER_UNAVAILABLE" to R.string.error_auth_provider,
        "AUTH.NO_ACCOUNT_AVAILABLE" to R.string.error_auth_no_account,
        "AUTH.TOKEN_EXPIRED" to R.string.error_auth_session_expired,
        "AUTH.PERMISSION_DENIED" to R.string.error_auth_permission_denied,
        "AUTH.REQUIRES_RECENT_LOGIN" to R.string.error_auth_requires_recent_login,
        "AUTH.UID_WOULD_CHANGE" to R.string.error_auth_uid_would_change,
        "AUTH.ACCOUNT_DELETION_REMOTE_FAILED" to R.string.error_account_deletion_remote_failed,
        "AUTH.UNKNOWN" to R.string.error_unexpected,
        "PERSISTENCE.DATABASE_UNAVAILABLE" to R.string.error_persistence,
        "PERSISTENCE.TRANSACTION_FAILED" to R.string.error_persistence,
        "PERSISTENCE.MIGRATION_FAILED" to R.string.error_persistence,
        "PERSISTENCE.SERIALIZATION_FAILED" to R.string.error_persistence,
        "PERSISTENCE.CONSTRAINT_VIOLATION" to R.string.error_persistence,
        "SYNC.RETRYABLE_NETWORK" to R.string.error_backup_offline,
        "SYNC.AUTH_EXPIRED" to R.string.error_backup_session,
        "SYNC.PERMISSION_DENIED" to R.string.error_backup_rejected,
        "SYNC.VALIDATION_REJECTED" to R.string.error_backup_rejected,
        "SYNC.PAYLOAD_POISONED" to R.string.error_backup_rejected,
        "SYNC.CONFLICT_UNRESOLVED" to R.string.error_restore,
        "SYNC.REMOTE_UNAVAILABLE" to R.string.error_backup_offline,
        "REMOTE.UNAVAILABLE" to R.string.error_backup_offline,
        "REMOTE.DEADLINE_EXCEEDED" to R.string.error_backup_offline,
        "REMOTE.PERMISSION_DENIED" to R.string.error_backup_rejected,
        "REMOTE.UNAUTHENTICATED" to R.string.error_backup_session,
        "REMOTE.INVALID_ARGUMENT" to R.string.error_backup_rejected,
        "REMOTE.NOT_FOUND" to R.string.error_restore,
        "REMOTE.UNKNOWN" to R.string.error_restore,
        "SECURITY.RULES_REJECTED" to R.string.error_backup_rejected,
        "SECURITY.OWNER_MISMATCH" to R.string.error_backup_rejected,
        "UNEXPECTED" to R.string.error_unexpected,
        "CONFIRMATION.OdometerInconsistent" to R.string.odometer_warning_body,
        "CONFIRMATION.DiscardPendingChanges" to R.string.pending_sync_title,
        "CONFIRMATION.DeleteAccount" to R.string.confirm_delete_account,
        "CONFIRMATION.DeleteLocalData" to R.string.confirm_delete_local_data,
        "CONFIRMATION.AdoptExistingAccount" to R.string.confirm_replace_existing_account,
        "INFO.CONFIRM_DELETE_VEHICLE" to R.string.delete_vehicle_confirmation,
        "INFO.CONFIRM_DELETE_FUEL_ENTRY" to R.string.delete_fuel_entry_confirmation,
    )
