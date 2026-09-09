package com.ruizurraca.carapp.core.database

import app.cash.sqldelight.async.coroutines.awaitAsOne

/**
 * The destructive local-data clear of `docs/CONTRACTS.md §11.5` and `docs/SPECIFICATION.md §7 F-5`.
 *
 * Sign-out, anonymous "delete local data" and account deletion all end by clearing every local
 * table the application owns, including `user_settings`, `outbox`, `sync_cursor`, `quarantine`, the
 * anonymous reminder position and the account-conversion marker. The clear runs in a single
 * transaction, so a partial clear is never observable and a failure rolls the whole thing back.
 *
 * `local_sequence` is the one table that is reset rather than emptied. It is a single control row
 * that assigns `localMutationSeq`, and `docs/TECHNICAL_PLAN.md §6` defines its canonical initial
 * state as `(id = 0, next = 1)`. Deleting the row would leave the sequence unable to assign a value
 * at all, so the clear restores that state inside the same transaction.
 */
class LocalDataClearDatabaseAccess(
    private val database: AppDatabase,
) {
    private val queries = database.databaseQueries

    suspend fun pendingOutboxCount(): Long = queries.countOutboxRows().awaitAsOne()

    suspend fun localSequenceNext(): Long = queries.selectLocalSequenceNext().awaitAsOne()

    suspend fun clearAllLocalData() {
        database.transaction {
            queries.deleteAllFuelEntries()
            queries.deleteAllVehicles()
            queries.deleteAllOutbox()
            queries.deleteAllSyncCursors()
            queries.deleteAllQuarantine()
            queries.deleteSettings()
            queries.deleteAnonymousReminder()
            queries.deleteAccountConversionSnapshots()
            queries.deleteAccountConversionOperation()
            // Restore the control row before resetting it, so a database whose row was somehow lost
            // still ends in the canonical initial state rather than with no sequence at all.
            queries.restoreLocalSequenceRow()
            queries.resetLocalSequence()
        }
    }
}
