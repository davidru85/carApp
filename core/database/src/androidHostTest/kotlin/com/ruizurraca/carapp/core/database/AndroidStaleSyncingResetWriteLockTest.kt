package com.ruizurraca.carapp.core.database

import app.cash.sqldelight.async.coroutines.await
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `E3-21` write-lock property, Android host half (`D-207`, which adopts the `D-194` count gate).
 *
 * The reset runs on every app start, and almost every start has nothing to reset. The driver opens
 * every `database.transaction { }` with `BEGIN IMMEDIATE` and the bundled stack sets no
 * `busy_timeout`, so an unconditional reset would make each start a writing transaction that fails
 * with `SQLITE_BUSY` against a concurrent writer, exactly as the first `E3-07` purge did. The reset
 * must therefore only read when nothing is stale. File-backed, with a second connection, because the
 * contention is between connections. `IosStaleSyncingResetWriteLockTest` is the iOS half.
 */
class AndroidStaleSyncingResetWriteLockTest {
    @Test
    fun anEmptyResetSucceedsWhileAnotherConnectionHoldsTheWriteLock() =
        runTest {
            withFileBackedDatabase("carapp-e3-21-lock-") { path, handle ->
                handle.insertVehicleForMutationTest(VEHICLE_ID)
                val writer = SecondConnection(path)
                try {
                    writer.holdWriteLock()
                    // With nothing stale this must be a pure read; a writing transaction would raise
                    // `SQLITE_BUSY` here because `BEGIN IMMEDIATE` cannot wait for the held lock.
                    SyncDatabaseAccess(handle.database).resetStaleSyncing()
                } finally {
                    writer.releaseWriteLock()
                    writer.close()
                }
                assertEquals("PENDING", handle.vehicleSyncState(VEHICLE_ID))
            }
        }

    @Test
    fun aResetWithWorkToDoStillResetsTheRow() =
        runTest {
            withFileBackedDatabase("carapp-e3-21-reset-") { _, handle ->
                handle.insertVehicleForMutationTest(VEHICLE_ID)
                handle.driver
                    .execute(null, "UPDATE vehicle SET syncState = 'SYNCING' WHERE id = '$VEHICLE_ID'", 0)
                    .await()

                SyncDatabaseAccess(handle.database).resetStaleSyncing()

                assertEquals("PENDING", handle.vehicleSyncState(VEHICLE_ID))
            }
        }

    private suspend fun TestDatabase.vehicleSyncState(id: String): String? =
        driver.nullableString("SELECT syncState FROM vehicle WHERE id = '$id'")

    private companion object {
        const val VEHICLE_ID = "vehicle-1"
    }
}
