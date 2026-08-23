package com.ruizurraca.carapp.core.database

import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FuelEntryTombstoneRecomputeTest {
    private lateinit var testDatabase: TestDatabase

    @AfterTest
    fun tearDown() {
        if (::testDatabase.isInitialized) testDatabase.close()
    }

    @Test
    fun tombstoneRecomputesOnlyPreDeleteSuccessorAndVehicleMaximum() =
        runTest {
            val database = createDatabase()
            database.insertVehicleForMutationTest(currentOdometerKm = 999)
            database.insertEntry("entry-a", date = 10, odometerKm = 100, inconsistent = 1)
            database.insertEntry("entry-b", date = 20, odometerKm = 300, inconsistent = 0)
            database.insertEntry("entry-c", date = 30, odometerKm = 200, inconsistent = 1)
            database.insertEntry("entry-d", date = 40, odometerKm = 400, inconsistent = 1)

            DatabaseMutations(database.database).tombstoneFuelEntry(
                id = "entry-b",
                deletedAt = 50,
                updatedAt = 50,
                syncState = "PENDING",
                localRevision = 2,
                localMutationSeq = 2,
            )

            assertEquals(1L, database.fuelEntryInconsistent("entry-a"))
            assertEquals(0L, database.fuelEntryInconsistent("entry-c"))
            assertEquals(1L, database.fuelEntryInconsistent("entry-d"))
            assertEquals(400L, database.vehicleCurrentOdometer())
        }

    private suspend fun TestDatabase.insertEntry(
        id: String,
        date: Long,
        odometerKm: Long,
        inconsistent: Long,
    ) {
        insertFuelEntryForMutationTest(
            id = id,
            date = date,
            createdAt = date,
            odometerKm = odometerKm,
            odometerInconsistent = inconsistent,
        )
    }

    private fun createDatabase(): TestDatabase = TestDatabase.create().also { testDatabase = it }
}
