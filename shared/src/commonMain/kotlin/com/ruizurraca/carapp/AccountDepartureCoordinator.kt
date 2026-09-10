package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.auth.AuthClient
import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.PersistenceError
import com.ruizurraca.carapp.core.database.AccountDepartureDatabaseAccess
import com.ruizurraca.carapp.core.database.DepartureOperationKind
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
     * Finishes a departure that a process death interrupted. Behavior-free seam for now.
     */
    suspend fun resumePending(): Outcome<Unit, AppError> = Outcome.Ok(Unit)

    private inline fun <T> persisted(block: () -> T): Outcome<T, AppError> =
        try {
            Outcome.Ok(block())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            Outcome.Err(PersistenceError.TransactionFailed)
        }
}
