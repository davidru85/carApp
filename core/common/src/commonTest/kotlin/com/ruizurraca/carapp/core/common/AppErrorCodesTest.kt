package com.ruizurraca.carapp.core.common

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every stable code of `docs/CONTRACTS.md §20.2`, asserted leaf by leaf.
 *
 * The codes are the only strings allowed to reach logs and analytics (`§17`), and
 * `CONNECTIVITY_ERROR_CODES` and the Firestore rules refer to them as literals. A rename that
 * compiled would silently break those references, so each one is pinned here.
 */
class AppErrorCodesTest {
    private val allErrors: List<AppError> =
        listOf(
            ValidationError.RequiredField("name"),
            ValidationError.InvalidLength("name", 1, 40),
            ValidationError.OutOfRange("odometerKm", 0, 9_999_999),
            ValidationError.EditNotAllowed("initialOdometerKm"),
            ValidationError.DuplicateName("Golf"),
            ValidationError.FutureDate,
            ValidationError.InvalidMoneyInput,
            ValidationError.NoOp,
            ValidationError.InvalidUnit("currency"),
            ValidationError.EntityDeleted,
            ValidationError.EntityNotFound,
            ValidationWarning.OdometerInconsistent(100L, 90L),
            ValidationWarning.PendingSyncBeforeSignOut(3),
            AuthError.Cancelled,
            AuthError.NetworkUnavailable,
            AuthError.CredentialAlreadyInUse,
            AuthError.ProviderUnavailable,
            AuthError.TokenExpired,
            AuthError.PermissionDenied,
            AuthError.RequiresRecentLogin,
            AuthError.UidWouldChange,
            AuthError.AccountDeletionRemoteFailed,
            AuthError.Unknown,
            PersistenceError.DatabaseUnavailable,
            PersistenceError.TransactionFailed,
            PersistenceError.MigrationFailed,
            PersistenceError.SerializationFailed,
            PersistenceError.ConstraintViolation,
            SyncError.RetryableNetwork,
            SyncError.AuthExpired,
            SyncError.PermissionDenied,
            SyncError.ValidationRejected,
            SyncError.PayloadPoisoned,
            SyncError.ConflictUnresolved,
            SyncError.RemoteUnavailable,
            RemoteError.Unavailable,
            RemoteError.DeadlineExceeded,
            RemoteError.PermissionDenied,
            RemoteError.Unauthenticated,
            RemoteError.InvalidArgument,
            RemoteError.NotFound,
            RemoteError.Unknown,
            SecurityError.RulesRejected,
            SecurityError.OwnerMismatch,
            UnexpectedError(origin = ":integration:firebase-firestore", throwableClassName = "IllegalStateException"),
        )

    @Test
    fun everyLeafCarriesItsDocumentedCode() {
        assertEquals(
            listOf(
                "VALIDATION.REQUIRED_FIELD",
                "VALIDATION.INVALID_LENGTH",
                "VALIDATION.OUT_OF_RANGE",
                "VALIDATION.EDIT_NOT_ALLOWED",
                "VALIDATION.DUPLICATE_NAME",
                "VALIDATION.FUTURE_DATE",
                "VALIDATION.INVALID_MONEY_INPUT",
                "VALIDATION.NO_OP",
                "VALIDATION.INVALID_UNIT",
                "VALIDATION.ENTITY_DELETED",
                "VALIDATION.ENTITY_NOT_FOUND",
                "WARNING.ODOMETER_INCONSISTENT",
                "WARNING.PENDING_SYNC",
                "AUTH.CANCELLED",
                "AUTH.NETWORK_UNAVAILABLE",
                "AUTH.CREDENTIAL_ALREADY_IN_USE",
                "AUTH.PROVIDER_UNAVAILABLE",
                "AUTH.TOKEN_EXPIRED",
                "AUTH.PERMISSION_DENIED",
                "AUTH.REQUIRES_RECENT_LOGIN",
                "AUTH.UID_WOULD_CHANGE",
                "AUTH.ACCOUNT_DELETION_REMOTE_FAILED",
                "AUTH.UNKNOWN",
                "PERSISTENCE.DATABASE_UNAVAILABLE",
                "PERSISTENCE.TRANSACTION_FAILED",
                "PERSISTENCE.MIGRATION_FAILED",
                "PERSISTENCE.SERIALIZATION_FAILED",
                "PERSISTENCE.CONSTRAINT_VIOLATION",
                "SYNC.RETRYABLE_NETWORK",
                "SYNC.AUTH_EXPIRED",
                "SYNC.PERMISSION_DENIED",
                "SYNC.VALIDATION_REJECTED",
                "SYNC.PAYLOAD_POISONED",
                "SYNC.CONFLICT_UNRESOLVED",
                "SYNC.REMOTE_UNAVAILABLE",
                "REMOTE.UNAVAILABLE",
                "REMOTE.DEADLINE_EXCEEDED",
                "REMOTE.PERMISSION_DENIED",
                "REMOTE.UNAUTHENTICATED",
                "REMOTE.INVALID_ARGUMENT",
                "REMOTE.NOT_FOUND",
                "REMOTE.UNKNOWN",
                "SECURITY.RULES_REJECTED",
                "SECURITY.OWNER_MISMATCH",
                "UNEXPECTED",
            ),
            allErrors.map { it.code },
        )
    }

    @Test
    fun codesAreUniqueAcrossTheWholeTaxonomy() {
        val codes = allErrors.map { it.code }
        assertEquals(
            codes.size,
            codes.toSet().size,
            "Duplicate AppError codes: ${codes.groupBy { it }.filterValues { it.size > 1 }.keys}",
        )
    }

    @Test
    fun confirmationCoversExactlyTheFourDocumentedFlows() {
        assertEquals(
            listOf(
                Confirmation.OdometerInconsistent,
                Confirmation.DiscardPendingChanges,
                Confirmation.DeleteAccount,
                Confirmation.AdoptExistingAccount,
            ),
            Confirmation.entries.toList(),
        )
    }

    @Test
    fun authProviderIsTheClosedMvpSet() {
        assertEquals(
            listOf(AuthProvider.ANONYMOUS, AuthProvider.GOOGLE, AuthProvider.APPLE),
            AuthProvider.entries.toList(),
            "docs/SPECIFICATION.md §7 F-1: the MVP has no sign-in method beyond these three",
        )
    }

    @Test
    fun syncTriggerCoversTheFiveDocumentedReasons() {
        assertEquals(
            listOf(
                SyncTrigger.AppForeground,
                SyncTrigger.ConnectivityRecovered,
                SyncTrigger.PostWriteDebounce,
                SyncTrigger.PullToRefresh,
                SyncTrigger.Periodic,
            ),
            SyncTrigger.entries.toList(),
        )
    }
}
