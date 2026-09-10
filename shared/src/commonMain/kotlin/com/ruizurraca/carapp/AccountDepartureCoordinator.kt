package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.auth.AuthClient
import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.PersistenceError
import com.ruizurraca.carapp.core.database.AccountDepartureDatabaseAccess
import com.ruizurraca.carapp.core.database.DepartureOperationKind
import com.ruizurraca.carapp.core.database.DepartureOperationRow
import com.ruizurraca.carapp.core.database.DepartureOperationStep
import com.ruizurraca.carapp.core.database.LocalDataClearDatabaseAccess
import kotlinx.coroutines.CancellationException

/**
 * The F-5 local-data side of sign-out and account deletion, `docs/CONTRACTS.md §11.5`.
 *
 * It owns the database facts the session holder needs: how many outbox rows are still pending
 * (the sign-out warning), the single-transaction clear of every local table, which also restores
 * `local_sequence` to its canonical initial state, and the durable departure marker that lets an
 * interrupted departure be finished at the next launch. It performs no server operation; the server
 * account deletion is the `AuthClient.deleteAccount()` call the session holder makes before this
 * clear runs.
 *
 * Both operations report a typed `PersistenceError` rather than an opaque failure, because the
 * session holder publishes that error to the owner unchanged: a database fault during a destructive
 * departure MUST NOT be reported as an authentication problem.
 */
internal class AccountDepartureCoordinator(
    private val databaseAccess: LocalDataClearDatabaseAccess,
    private val departureAccess: AccountDepartureDatabaseAccess,
    private val authClient: AuthClient,
) : AccountDepartureHandler {
    override suspend fun pendingOutboxCount(): Outcome<Int, AppError> =
        persisted { databaseAccess.pendingOutboxCount().toInt() }

    override suspend fun clearLocalData(): Outcome<Unit, AppError> = persisted { databaseAccess.clearAllLocalData() }

    override suspend fun startPersistedDeparture(
        kind: DepartureOperationKind,
        ownerUid: String?,
    ): Outcome<Unit, AppError> = persisted { departureAccess.start(kind, ownerUid) }

    override suspend fun markDepartureStep(step: DepartureOperationStep): Outcome<Unit, AppError> =
        persisted { departureAccess.markStep(step) }

    override suspend fun clearPersistedDeparture(): Outcome<Unit, AppError> = persisted { departureAccess.clear() }

    /**
     * Finishes a departure that a process death interrupted (`D-167`). It runs at graph
     * construction, repeats only the steps the marker does not record as done, and reports nothing
     * to the owner: the departure was already authorised, so this is the app finishing its own job.
     */
    suspend fun resumePending(): Outcome<Unit, AppError> {
        val loaded = persisted { departureAccess.load() }
        if (loaded is Outcome.Err) return loaded
        val row = (loaded as Outcome.Ok).value ?: return Outcome.Ok(Unit)
        // A permanent deletion whose `D-23` call never recorded success has nothing destructive to
        // finish. The server operation MUST NOT be repeated, and MUST NOT be started without a
        // confirmation nobody gave, so the marker is dropped and the owner keeps account and data.
        if (row.kind == DepartureOperationKind.DELETE_PERMANENT && !row.remoteDone) {
            return clearPersistedDeparture()
        }
        return finish(row)
    }

    private suspend fun finish(row: DepartureOperationRow): Outcome<Unit, AppError> {
        for (step in stepsStillOwed(row)) {
            val performed =
                when (step) {
                    DepartureOperationStep.SESSION_CLEANUP -> endProviderSession()

                    DepartureOperationStep.LOCAL_CLEAR -> clearLocalData()

                    // Never resumed: see `resumePending`.
                    DepartureOperationStep.REMOTE_DELETION -> Outcome.Ok(Unit)
                }
            if (performed is Outcome.Err) return performed
            val marked = markDepartureStep(step)
            if (marked is Outcome.Err) return marked
        }
        return clearPersistedDeparture()
    }

    /**
     * An anonymous deletion clears local data and ends the session afterwards; every other kind ends
     * the session first. A local owner has no session to end.
     */
    private fun stepsStillOwed(row: DepartureOperationRow): List<DepartureOperationStep> {
        val sessionOwed =
            row.kind != DepartureOperationKind.DELETE_LOCAL && !row.sessionEnded
        val localOwed = !row.localDone
        return buildList {
            if (row.kind == DepartureOperationKind.DELETE_ANONYMOUS) {
                if (localOwed) add(DepartureOperationStep.LOCAL_CLEAR)
                if (sessionOwed) add(DepartureOperationStep.SESSION_CLEANUP)
            } else {
                if (sessionOwed) add(DepartureOperationStep.SESSION_CLEANUP)
                if (localOwed) add(DepartureOperationStep.LOCAL_CLEAR)
            }
        }
    }

    private suspend fun endProviderSession(): Outcome<Unit, AppError> =
        when (val result = authClient.signOut()) {
            is Outcome.Err -> Outcome.Err(result.error)
            is Outcome.Ok -> Outcome.Ok(Unit)
        }

    private inline fun <T> persisted(block: () -> T): Outcome<T, AppError> =
        try {
            Outcome.Ok(block())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            Outcome.Err(PersistenceError.TransactionFailed)
        }
}
