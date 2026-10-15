package com.ruizurraca.carapp.core.database

import app.cash.sqldelight.async.coroutines.await
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `E3-21` write-lock property, iOS half (`D-207`, which adopts the `D-194` count gate).
 *
 * iOS is the platform whose persistent file surfaced the `E3-07` `SQLITE_BUSY` failure, when two graphs
 * mounted on one file and an unconditional startup transaction could not wait for the other writer.
 * The reset runs on every start, so with nothing stale it must only read. The assertions mirror
 * `AndroidStaleSyncingResetWriteLockTest` on the real iOS file and driver.
 */
@OptIn(ExperimentalForeignApi::class)
class IosStaleSyncingResetWriteLockTest {
    @Test
    fun anEmptyResetSucceedsWhileAnotherConnectionHoldsTheWriteLock() =
        runTest {
            withIosFileBackedDatabase { path, handle ->
                handle.insertVehicleForMutationTest(VEHICLE_ID)
                val writer = IosSecondConnection(path)
                try {
                    writer.holdWriteLock()
                    // A writing transaction would raise `SQLITE_BUSY` here; with nothing stale the
                    // reset must only read.
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
            withIosFileBackedDatabase { _, handle ->
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
