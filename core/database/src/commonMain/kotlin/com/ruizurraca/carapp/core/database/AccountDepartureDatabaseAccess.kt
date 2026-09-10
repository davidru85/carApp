package com.ruizurraca.carapp.core.database

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull

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
 * The durable F-5 departure marker of `docs/CONTRACTS.md §11.5` and `D-166`.
 *
 * A departure writes this row before its first destructive step and records each step as it
 * succeeds, so a relaunch after a process death finishes exactly what was left and repeats nothing.
 * At most one departure is ever in flight, so the row is pinned to `id = 0` like the other control
 * rows.
 *
 * `LocalDataClearDatabaseAccess.clearAllLocalData()` deliberately leaves this table alone. An
 * anonymous deletion clears local data and only then ends the provider session, so wiping the marker
 * inside the clear would make the very operation performing it unrecoverable.
 */
class AccountDepartureDatabaseAccess(
    private val database: AppDatabase,
) {
    private val queries = database.databaseQueries

    suspend fun load(): DepartureOperationRow? =
        queries
            .selectAccountDepartureOperation()
            .awaitAsOneOrNull()
            ?.let { row ->
                DepartureOperationRow(
                    kind = DepartureOperationKind.valueOf(row.kind),
                    ownerUid = row.ownerUid,
                    remoteDone = row.remoteDone == 1L,
                    sessionEnded = row.sessionEnded == 1L,
                    localDone = row.localDone == 1L,
                )
            }

    suspend fun start(
        kind: DepartureOperationKind,
        ownerUid: String?,
    ) {
        queries.startAccountDepartureOperation(kind.name, ownerUid)
    }

    suspend fun markStep(step: DepartureOperationStep) {
        when (step) {
            DepartureOperationStep.REMOTE_DELETION -> queries.markAccountDepartureRemoteDeletion()
            DepartureOperationStep.SESSION_CLEANUP -> queries.markAccountDepartureSessionCleanup()
            DepartureOperationStep.LOCAL_CLEAR -> queries.markAccountDepartureLocalClear()
        }
    }

    suspend fun clear() {
        queries.deleteAccountDepartureOperation()
    }
}
