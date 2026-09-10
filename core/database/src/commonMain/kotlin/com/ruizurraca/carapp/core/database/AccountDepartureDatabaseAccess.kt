package com.ruizurraca.carapp.core.database

/** The F-5 departure kinds, as persisted by the durable recovery marker. */
enum class DepartureOperationKind {
    SIGN_OUT,
    DELETE_LOCAL,
    DELETE_ANONYMOUS,
    DELETE_PERMANENT,
}

/** The steps a departure records as it completes them, so a relaunch repeats none of them. */
enum class DepartureOperationStep {
    REMOTE_DELETION,
    SESSION_CLEANUP,
    LOCAL_CLEAR,
}

/** One interrupted F-5 departure, as read back from the durable marker. */
data class DepartureOperationRow(
    val kind: DepartureOperationKind,
    val ownerUid: String?,
    val remoteDone: Boolean,
    val sessionEnded: Boolean,
    val localDone: Boolean,
)

/**
 * The durable F-5 departure marker of `docs/CONTRACTS.md §11.5`.
 *
 * Behavior-free seam: it declares the surface the RED specification exercises, so those tests
 * compile and fail for the missing behavior rather than for a missing type.
 */
class AccountDepartureDatabaseAccess(
    private val database: AppDatabase,
) {
    suspend fun load(): DepartureOperationRow? = null

    suspend fun start(
        kind: DepartureOperationKind,
        ownerUid: String?,
    ) = Unit

    suspend fun markStep(step: DepartureOperationStep) = Unit

    suspend fun clear() = Unit
}
