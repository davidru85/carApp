package com.ruizurraca.carapp.core.common

/**
 * The error taxonomy of `docs/CONTRACTS.md §20.2`.
 *
 * Every leaf carries a stable [AppError.code]. The codes are the only strings allowed to reach
 * logs and analytics (`§17`), so they MUST NOT be renamed without a contract change.
 */
sealed interface AppError {
    val code: String
}

sealed interface ValidationError : AppError {
    data class RequiredField(
        val field: String,
    ) : ValidationError {
        override val code = "VALIDATION.REQUIRED_FIELD"
    }

    data class InvalidLength(
        val field: String,
        val min: Int,
        val max: Int,
    ) : ValidationError {
        override val code = "VALIDATION.INVALID_LENGTH"
    }

    data class OutOfRange(
        val field: String,
        val min: Long,
        val max: Long,
    ) : ValidationError {
        override val code = "VALIDATION.OUT_OF_RANGE"
    }

    data class EditNotAllowed(
        val field: String,
    ) : ValidationError {
        override val code = "VALIDATION.EDIT_NOT_ALLOWED"
    }

    data class DuplicateName(
        val name: String,
    ) : ValidationError {
        override val code = "VALIDATION.DUPLICATE_NAME"
    }

    data object FutureDate : ValidationError {
        override val code = "VALIDATION.FUTURE_DATE"
    }

    data object InvalidMoneyInput : ValidationError {
        override val code = "VALIDATION.INVALID_MONEY_INPUT"
    }

    data object NoOp : ValidationError {
        override val code = "VALIDATION.NO_OP"
    }

    data class InvalidUnit(
        val detail: String,
    ) : ValidationError {
        override val code = "VALIDATION.INVALID_UNIT"
    }

    data object EntityDeleted : ValidationError {
        override val code = "VALIDATION.ENTITY_DELETED"
    }

    data object EntityNotFound : ValidationError {
        override val code = "VALIDATION.ENTITY_NOT_FOUND"
    }
}

sealed interface ValidationWarning : AppError {
    data class OdometerInconsistent(
        val previousOdometerKm: Long,
        val enteredOdometerKm: Long,
    ) : ValidationWarning {
        override val code = "WARNING.ODOMETER_INCONSISTENT"
    }

    data class PendingSyncBeforeSignOut(
        val pendingCount: Int,
    ) : ValidationWarning {
        override val code = "WARNING.PENDING_SYNC"
    }
}

sealed interface AuthError : AppError {
    data object Cancelled : AuthError {
        override val code = "AUTH.CANCELLED"
    }

    data object NetworkUnavailable : AuthError {
        override val code = "AUTH.NETWORK_UNAVAILABLE"
    }

    data object CredentialAlreadyInUse : AuthError {
        override val code = "AUTH.CREDENTIAL_ALREADY_IN_USE"
    }

    data object ProviderUnavailable : AuthError {
        override val code = "AUTH.PROVIDER_UNAVAILABLE"
    }

    data object TokenExpired : AuthError {
        override val code = "AUTH.TOKEN_EXPIRED"
    }

    data object PermissionDenied : AuthError {
        override val code = "AUTH.PERMISSION_DENIED"
    }

    data object RequiresRecentLogin : AuthError {
        override val code = "AUTH.REQUIRES_RECENT_LOGIN"
    }

    data object UidWouldChange : AuthError {
        override val code = "AUTH.UID_WOULD_CHANGE"
    }

    data object AccountDeletionRemoteFailed : AuthError {
        override val code = "AUTH.ACCOUNT_DELETION_REMOTE_FAILED"
    }

    data object Unknown : AuthError {
        override val code = "AUTH.UNKNOWN"
    }
}

sealed interface PersistenceError : AppError {
    data object DatabaseUnavailable : PersistenceError {
        override val code = "PERSISTENCE.DATABASE_UNAVAILABLE"
    }

    data object TransactionFailed : PersistenceError {
        override val code = "PERSISTENCE.TRANSACTION_FAILED"
    }

    data object MigrationFailed : PersistenceError {
        override val code = "PERSISTENCE.MIGRATION_FAILED"
    }

    data object SerializationFailed : PersistenceError {
        override val code = "PERSISTENCE.SERIALIZATION_FAILED"
    }

    data object ConstraintViolation : PersistenceError {
        override val code = "PERSISTENCE.CONSTRAINT_VIOLATION"
    }
}

sealed interface SyncError : AppError {
    data object RetryableNetwork : SyncError {
        override val code = "SYNC.RETRYABLE_NETWORK"
    }

    data object AuthExpired : SyncError {
        override val code = "SYNC.AUTH_EXPIRED"
    }

    data object PermissionDenied : SyncError {
        override val code = "SYNC.PERMISSION_DENIED"
    }

    data object ValidationRejected : SyncError {
        override val code = "SYNC.VALIDATION_REJECTED"
    }

    data object PayloadPoisoned : SyncError {
        override val code = "SYNC.PAYLOAD_POISONED"
    }

    data object ConflictUnresolved : SyncError {
        override val code = "SYNC.CONFLICT_UNRESOLVED"
    }

    data object RemoteUnavailable : SyncError {
        override val code = "SYNC.REMOTE_UNAVAILABLE"
    }
}

sealed interface RemoteError : AppError {
    data object Unavailable : RemoteError {
        override val code = "REMOTE.UNAVAILABLE"
    }

    data object DeadlineExceeded : RemoteError {
        override val code = "REMOTE.DEADLINE_EXCEEDED"
    }

    data object PermissionDenied : RemoteError {
        override val code = "REMOTE.PERMISSION_DENIED"
    }

    data object Unauthenticated : RemoteError {
        override val code = "REMOTE.UNAUTHENTICATED"
    }

    data object InvalidArgument : RemoteError {
        override val code = "REMOTE.INVALID_ARGUMENT"
    }

    data object NotFound : RemoteError {
        override val code = "REMOTE.NOT_FOUND"
    }

    data object Unknown : RemoteError {
        override val code = "REMOTE.UNKNOWN"
    }
}

sealed interface SecurityError : AppError {
    data object RulesRejected : SecurityError {
        override val code = "SECURITY.RULES_REJECTED"
    }

    data object OwnerMismatch : SecurityError {
        override val code = "SECURITY.OWNER_MISMATCH"
    }
}

/** [origin] is the Gradle module path that converted the failure, e.g. `":integration:firebase-firestore"`. */
data class UnexpectedError(
    val origin: String,
    val throwableClassName: String,
) : AppError {
    override val code = "UNEXPECTED"
}

/**
 * A confirmation is required by the use case, not by the UI. The UI MUST NOT proceed without an
 * explicit confirmation returned to the use case (`docs/CONTRACTS.md §20.2`).
 */
enum class Confirmation {
    OdometerInconsistent,
    DiscardPendingChanges,
    DeleteAccount,
    AdoptExistingAccount,
}
