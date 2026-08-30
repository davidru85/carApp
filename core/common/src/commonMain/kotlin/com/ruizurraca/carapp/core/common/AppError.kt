@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package com.ruizurraca.carapp.core.common

import kotlin.native.HiddenFromObjC

/**
 * The error taxonomy of `docs/CONTRACTS.md §20.2`.
 *
 * Every leaf carries a stable [AppError.code]. The codes are the only strings allowed to reach
 * logs and analytics (`§17`), so they MUST NOT be renamed without a contract change.
 */
@HiddenFromObjC
sealed interface AppError {
    val code: String
}

@HiddenFromObjC
sealed interface ValidationError : AppError {
    @HiddenFromObjC
    data class RequiredField(
        val field: String,
    ) : ValidationError {
        override val code = "VALIDATION.REQUIRED_FIELD"
    }

    @HiddenFromObjC
    data class InvalidLength(
        val field: String,
        val min: Int,
        val max: Int,
    ) : ValidationError {
        override val code = "VALIDATION.INVALID_LENGTH"
    }

    @HiddenFromObjC
    data class OutOfRange(
        val field: String,
        val min: Long,
        val max: Long,
    ) : ValidationError {
        override val code = "VALIDATION.OUT_OF_RANGE"
    }

    @HiddenFromObjC
    data class EditNotAllowed(
        val field: String,
    ) : ValidationError {
        override val code = "VALIDATION.EDIT_NOT_ALLOWED"
    }

    @HiddenFromObjC
    data class DuplicateName(
        val name: String,
    ) : ValidationError {
        override val code = "VALIDATION.DUPLICATE_NAME"
    }

    @HiddenFromObjC
    data object FutureDate : ValidationError {
        override val code = "VALIDATION.FUTURE_DATE"
    }

    @HiddenFromObjC
    data object InvalidMoneyInput : ValidationError {
        override val code = "VALIDATION.INVALID_MONEY_INPUT"
    }

    @HiddenFromObjC
    data object NoOp : ValidationError {
        override val code = "VALIDATION.NO_OP"
    }

    @HiddenFromObjC
    data class InvalidUnit(
        val detail: String,
    ) : ValidationError {
        override val code = "VALIDATION.INVALID_UNIT"
    }

    @HiddenFromObjC
    data object EntityDeleted : ValidationError {
        override val code = "VALIDATION.ENTITY_DELETED"
    }

    @HiddenFromObjC
    data object EntityNotFound : ValidationError {
        override val code = "VALIDATION.ENTITY_NOT_FOUND"
    }
}

@HiddenFromObjC
sealed interface ValidationWarning : AppError {
    @HiddenFromObjC
    data class OdometerInconsistent(
        val previousOdometerKm: Long,
        val enteredOdometerKm: Long,
    ) : ValidationWarning {
        override val code = "WARNING.ODOMETER_INCONSISTENT"
    }

    @HiddenFromObjC
    data class PendingSyncBeforeSignOut(
        val pendingCount: Int,
    ) : ValidationWarning {
        override val code = "WARNING.PENDING_SYNC"
    }
}

@HiddenFromObjC
sealed interface AuthError : AppError {
    @HiddenFromObjC
    data object Cancelled : AuthError {
        override val code = "AUTH.CANCELLED"
    }

    @HiddenFromObjC
    data object NetworkUnavailable : AuthError {
        override val code = "AUTH.NETWORK_UNAVAILABLE"
    }

    @HiddenFromObjC
    data object CredentialAlreadyInUse : AuthError {
        override val code = "AUTH.CREDENTIAL_ALREADY_IN_USE"
    }

    @HiddenFromObjC
    data object ProviderUnavailable : AuthError {
        override val code = "AUTH.PROVIDER_UNAVAILABLE"
    }

    @HiddenFromObjC
    data object TokenExpired : AuthError {
        override val code = "AUTH.TOKEN_EXPIRED"
    }

    @HiddenFromObjC
    data object PermissionDenied : AuthError {
        override val code = "AUTH.PERMISSION_DENIED"
    }

    @HiddenFromObjC
    data object RequiresRecentLogin : AuthError {
        override val code = "AUTH.REQUIRES_RECENT_LOGIN"
    }

    @HiddenFromObjC
    data object UidWouldChange : AuthError {
        override val code = "AUTH.UID_WOULD_CHANGE"
    }

    @HiddenFromObjC
    data object AccountDeletionRemoteFailed : AuthError {
        override val code = "AUTH.ACCOUNT_DELETION_REMOTE_FAILED"
    }

    @HiddenFromObjC
    data object Unknown : AuthError {
        override val code = "AUTH.UNKNOWN"
    }
}

@HiddenFromObjC
sealed interface PersistenceError : AppError {
    @HiddenFromObjC
    data object DatabaseUnavailable : PersistenceError {
        override val code = "PERSISTENCE.DATABASE_UNAVAILABLE"
    }

    @HiddenFromObjC
    data object TransactionFailed : PersistenceError {
        override val code = "PERSISTENCE.TRANSACTION_FAILED"
    }

    @HiddenFromObjC
    data object MigrationFailed : PersistenceError {
        override val code = "PERSISTENCE.MIGRATION_FAILED"
    }

    @HiddenFromObjC
    data object SerializationFailed : PersistenceError {
        override val code = "PERSISTENCE.SERIALIZATION_FAILED"
    }

    @HiddenFromObjC
    data object ConstraintViolation : PersistenceError {
        override val code = "PERSISTENCE.CONSTRAINT_VIOLATION"
    }
}

@HiddenFromObjC
sealed interface SyncError : AppError {
    @HiddenFromObjC
    data object RetryableNetwork : SyncError {
        override val code = "SYNC.RETRYABLE_NETWORK"
    }

    @HiddenFromObjC
    data object AuthExpired : SyncError {
        override val code = "SYNC.AUTH_EXPIRED"
    }

    @HiddenFromObjC
    data object PermissionDenied : SyncError {
        override val code = "SYNC.PERMISSION_DENIED"
    }

    @HiddenFromObjC
    data object ValidationRejected : SyncError {
        override val code = "SYNC.VALIDATION_REJECTED"
    }

    @HiddenFromObjC
    data object PayloadPoisoned : SyncError {
        override val code = "SYNC.PAYLOAD_POISONED"
    }

    @HiddenFromObjC
    data object ConflictUnresolved : SyncError {
        override val code = "SYNC.CONFLICT_UNRESOLVED"
    }

    @HiddenFromObjC
    data object RemoteUnavailable : SyncError {
        override val code = "SYNC.REMOTE_UNAVAILABLE"
    }
}

@HiddenFromObjC
sealed interface RemoteError : AppError {
    @HiddenFromObjC
    data object Unavailable : RemoteError {
        override val code = "REMOTE.UNAVAILABLE"
    }

    @HiddenFromObjC
    data object DeadlineExceeded : RemoteError {
        override val code = "REMOTE.DEADLINE_EXCEEDED"
    }

    @HiddenFromObjC
    data object PermissionDenied : RemoteError {
        override val code = "REMOTE.PERMISSION_DENIED"
    }

    @HiddenFromObjC
    data object Unauthenticated : RemoteError {
        override val code = "REMOTE.UNAUTHENTICATED"
    }

    @HiddenFromObjC
    data object InvalidArgument : RemoteError {
        override val code = "REMOTE.INVALID_ARGUMENT"
    }

    @HiddenFromObjC
    data object NotFound : RemoteError {
        override val code = "REMOTE.NOT_FOUND"
    }

    @HiddenFromObjC
    data object Unknown : RemoteError {
        override val code = "REMOTE.UNKNOWN"
    }
}

@HiddenFromObjC
sealed interface SecurityError : AppError {
    @HiddenFromObjC
    data object RulesRejected : SecurityError {
        override val code = "SECURITY.RULES_REJECTED"
    }

    @HiddenFromObjC
    data object OwnerMismatch : SecurityError {
        override val code = "SECURITY.OWNER_MISMATCH"
    }
}

/** [origin] is the Gradle module path that converted the failure, e.g. `":integration:firebase-firestore"`. */
@HiddenFromObjC
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
