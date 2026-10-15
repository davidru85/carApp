package com.ruizurraca.carapp.core.database

import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

/**
 * `E3-21` / `D-207`: the startup reset of entity rows a process death left `SYNCING`.
 *
 * A stale row returns to the state it had before the interrupted attempt, which its outbox row still
 * records: `failPush`'s rule applied to `lastErrorCode` gives `FAILED_RETRYABLE` after a
 * non-connectivity failure and `PENDING` otherwise. The outbox row itself is never modified, so the
 * backoff and the poison budget of `§9.7` are unchanged.
 */
class StaleSyncingResetDatabaseAccessTest {
    @Test
    fun aStaleRowWhoseOutboxCarriesNoErrorReturnsToPending() =
        runTest {
            withDatabase {
                seedStaleVehicle(VEHICLE_ID, errorCode = null)

                resetStaleSyncing()

                assertEquals("PENDING", vehicleState(VEHICLE_ID))
            }
        }

    @Test
    fun aStaleRowAfterAConnectivityFailureReturnsToPending() =
        runTest {
            withDatabase {
                seedStaleVehicle(VEHICLE_ID, errorCode = "REMOTE.UNAVAILABLE")
                seedStaleFuelEntry(ENTRY_ID, errorCode = "REMOTE.DEADLINE_EXCEEDED")

                resetStaleSyncing()

                assertEquals("PENDING", vehicleState(VEHICLE_ID))
                assertEquals("PENDING", fuelEntryState(ENTRY_ID))
            }
        }

    @Test
    fun aStaleRowAfterANonConnectivityFailureReturnsToFailedRetryable() =
        runTest {
            withDatabase {
                seedStaleVehicle(VEHICLE_ID, errorCode = "REMOTE.UNKNOWN")
                seedStaleFuelEntry(ENTRY_ID, errorCode = "REMOTE.UNAUTHENTICATED")

                resetStaleSyncing()

                assertEquals("FAILED_RETRYABLE", vehicleState(VEHICLE_ID))
                assertEquals("FAILED_RETRYABLE", fuelEntryState(ENTRY_ID))
            }
        }

    @Test
    fun aStaleRowWithoutAnOutboxRowReturnsToPending() =
        runTest {
            withDatabase {
                // Not reachable through any write path (`§7`); D-207 still leaves no row `SYNCING`.
                insertVehicleForMutationTest(VEHICLE_ID)
                setVehicleState(VEHICLE_ID, "SYNCING")

                resetStaleSyncing()

                assertEquals("PENDING", vehicleState(VEHICLE_ID))
            }
        }

    @Test
    fun theResetLeavesTheOutboxRetryContextUnchanged() =
        runTest {
            withDatabase {
                seedStaleVehicle(VEHICLE_ID, errorCode = "REMOTE.UNKNOWN")
                val before = outboxContext(VEHICLE_ID)

                resetStaleSyncing()

                assertEquals(before, outboxContext(VEHICLE_ID))
                assertEquals(listOf("3", "900", "REMOTE.UNKNOWN", "cycle-old"), before)
            }
        }

    @Test
    fun rowsThatAreNotSyncingAreLeftUnchanged() =
        runTest {
            withDatabase {
                seedVehicle(VEHICLE_ID, state = "PENDING", errorCode = "REMOTE.UNAVAILABLE")
                seedVehicle(OTHER_VEHICLE_ID, state = "FAILED_POISONED", errorCode = "REMOTE.UNKNOWN")
                seedVehicle(SYNCED_VEHICLE_ID, state = "SYNCED", errorCode = null, withOutbox = false)
                // A stale anchor in the other table, so both reset statements really run.
                seedStaleFuelEntry(ENTRY_ID, errorCode = null)

                resetStaleSyncing()

                assertEquals("PENDING", vehicleState(VEHICLE_ID))
                assertEquals("FAILED_POISONED", vehicleState(OTHER_VEHICLE_ID))
                assertEquals("SYNCED", vehicleState(SYNCED_VEHICLE_ID))
                assertEquals("PENDING", fuelEntryState(ENTRY_ID), "the anchor proves the statements ran")
            }
        }

    @Test
    fun aFailedResetRollsBackBothTables() =
        runTest {
            withDatabase {
                seedStaleVehicle(VEHICLE_ID, errorCode = "REMOTE.UNKNOWN")
                seedStaleFuelEntry(ENTRY_ID, errorCode = null)
                // The abort is on the fuel-entry statement, so a vehicle reset that survived the
                // failure would prove the two statements were not one transaction.
                driver.exec(
                    """
                    CREATE TRIGGER fail_fuel_entry_reset BEFORE UPDATE OF syncState ON fuel_entry
                    BEGIN SELECT RAISE(ABORT, 'reset failed'); END
                    """.trimIndent(),
                )

                assertFails { resetStaleSyncing() }

                assertEquals("SYNCING", vehicleState(VEHICLE_ID), "the vehicle reset was rolled back")
                assertEquals("SYNCING", fuelEntryState(ENTRY_ID))
            }
        }

    private suspend fun withDatabase(block: suspend TestDatabase.() -> Unit) {
        val database = TestDatabase.create()
        try {
            database.block()
        } finally {
            database.close()
        }
    }

    private suspend fun TestDatabase.resetStaleSyncing() {
        SyncDatabaseAccess(database).resetStaleSyncing()
    }

    private suspend fun TestDatabase.seedStaleVehicle(
        id: String,
        errorCode: String?,
    ) {
        seedVehicle(id, state = "SYNCING", errorCode = errorCode)
    }

    private suspend fun TestDatabase.seedVehicle(
        id: String,
        state: String,
        errorCode: String?,
        withOutbox: Boolean = true,
    ) {
        insertVehicleForMutationTest(id)
        if (withOutbox) seedOutbox("VEHICLE", id, errorCode)
        setVehicleState(id, state)
    }

    private suspend fun TestDatabase.seedStaleFuelEntry(
        id: String,
        errorCode: String?,
    ) {
        if (vehicleState(VEHICLE_ID) == null) insertVehicleForMutationTest(VEHICLE_ID)
        insertFuelEntryForMutationTest(id = id, date = 1, createdAt = 1, odometerKm = 150)
        seedOutbox("FUEL_ENTRY", id, errorCode)
        driver.exec("UPDATE fuel_entry SET syncState = 'SYNCING' WHERE id = '$id'")
    }

    /** An outbox row, plus the retry context a previous resolved attempt left when [errorCode] is set. */
    private suspend fun TestDatabase.seedOutbox(
        entityType: String,
        id: String,
        errorCode: String?,
    ) {
        database.databaseQueries.coalesceOutbox(entityType, id, "{\"deleted\":false}", 1)
        if (errorCode != null) {
            SyncDatabaseAccess(database).failPush(entityType, id, 1, 3, 900, errorCode, false, "cycle-old")
        }
    }

    private suspend fun TestDatabase.setVehicleState(
        id: String,
        state: String,
    ) {
        driver.exec("UPDATE vehicle SET syncState = '$state' WHERE id = '$id'")
    }

    private suspend fun TestDatabase.vehicleState(id: String): String? =
        driver.nullableString("SELECT syncState FROM vehicle WHERE id = '$id'")

    private suspend fun TestDatabase.fuelEntryState(id: String): String? =
        driver.nullableString("SELECT syncState FROM fuel_entry WHERE id = '$id'")

    private suspend fun TestDatabase.outboxContext(id: String): List<String?> =
        listOf(
            driver.nullableLong("SELECT attemptCount FROM outbox WHERE entityId = '$id'")?.toString(),
            driver.nullableLong("SELECT nextAttemptAt FROM outbox WHERE entityId = '$id'")?.toString(),
            driver.nullableString("SELECT lastErrorCode FROM outbox WHERE entityId = '$id'"),
            driver.nullableString("SELECT cycleId FROM outbox WHERE entityId = '$id'"),
        )

    private suspend fun SqlDriver.exec(sql: String) {
        execute(identifier = null, sql = sql, parameters = 0).await()
    }

    private companion object {
        const val VEHICLE_ID = "vehicle-1"
        const val OTHER_VEHICLE_ID = "vehicle-2"
        const val SYNCED_VEHICLE_ID = "vehicle-3"
        const val ENTRY_ID = "entry-1"
    }
}
