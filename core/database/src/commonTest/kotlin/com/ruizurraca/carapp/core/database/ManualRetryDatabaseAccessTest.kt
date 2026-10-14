package com.ruizurraca.carapp.core.database

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * `E3-18` / `D-173` option B: manual retry (`resetFailedOutbox`, `§9.7`) also clears a row parked by a
 * connectivity-only failure, whose entity is already `PENDING`, so the retry control does not leave it
 * waiting out its backoff.
 */
class ManualRetryDatabaseAccessTest {
    @Test
    fun resetFailedMakesAParkedConnectivityRowDueAndClearsItsRetryContext() =
        runTest {
            val testDatabase = TestDatabase.create()
            try {
                testDatabase.insertVehicleForMutationTest()
                testDatabase.database.databaseQueries.coalesceOutbox("VEHICLE", "vehicle-1", "{\"deleted\":false}", 1)
                val access = SyncDatabaseAccess(testDatabase.database)
                access.failPush("VEHICLE", "vehicle-1", 1, 3, 900_000, "REMOTE.UNAVAILABLE", false, "cycle-old")
                assertEquals(emptyList(), access.dueOutbox(now = 100, limit = 50), "parked behind its backoff")

                access.resetFailed(now = 100)

                val due = access.dueOutbox(now = 100, limit = 50).single()
                assertEquals(0, due.attemptCount)
                assertNull(
                    testDatabase.driver.nullableString("SELECT lastErrorCode FROM outbox WHERE entityId = 'vehicle-1'"),
                )
                assertNull(
                    testDatabase.driver.nullableString("SELECT lastError FROM outbox WHERE entityId = 'vehicle-1'"),
                )
                assertNull(
                    testDatabase.driver.nullableString("SELECT cycleId FROM outbox WHERE entityId = 'vehicle-1'"),
                )
                assertEquals(
                    "PENDING",
                    testDatabase.driver.nullableString("SELECT syncState FROM vehicle WHERE id = 'vehicle-1'"),
                    "the entity state of a parked row is already PENDING and stays so",
                )
            } finally {
                testDatabase.close()
            }
        }
}
