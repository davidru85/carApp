package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.PersistenceError
import com.ruizurraca.carapp.core.database.LocalDataClearDatabaseAccess
import kotlinx.coroutines.CancellationException

/**
 * The F-5 local-data side of sign-out and account deletion, `docs/CONTRACTS.md §11.5`.
 *
 * It owns the two database facts the session holder needs: how many outbox rows are still pending
 * (the sign-out warning) and the single-transaction clear of every local table, which also restores
 * `local_sequence` to its canonical initial state. It performs no server operation; the server
 * account deletion is the `AuthClient.deleteAccount()` call the session holder makes before this
 * clear runs.
 *
 * Both operations report a typed `PersistenceError` rather than an opaque failure, because the
 * session holder publishes that error to the owner unchanged: a database fault during a destructive
 * departure MUST NOT be reported as an authentication problem.
 */
internal class AccountDepartureCoordinator(
    private val databaseAccess: LocalDataClearDatabaseAccess,
) : AccountDepartureHandler {
    override suspend fun pendingOutboxCount(): Outcome<Int, AppError> =
        try {
            Outcome.Ok(databaseAccess.pendingOutboxCount().toInt())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            Outcome.Err(PersistenceError.TransactionFailed)
        }

    override suspend fun clearLocalData(): Outcome<Unit, AppError> =
        try {
            databaseAccess.clearAllLocalData()
            Outcome.Ok(Unit)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            Outcome.Err(PersistenceError.TransactionFailed)
        }
}
