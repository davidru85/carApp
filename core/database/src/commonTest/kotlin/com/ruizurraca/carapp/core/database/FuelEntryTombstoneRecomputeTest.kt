package com.ruizurraca.carapp.core.database

import app.cash.sqldelight.async.coroutines.awaitAsOne
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

    @Test
    fun vehicleCascadeTombstonesThreeRowsWithoutRecomputingTombstonedSuccessors() =
        runTest {
            val database = createDatabase()
            database.insertVehicleForMutationTest(initialOdometerKm = 100, currentOdometerKm = 999)
            database.insertEntry("entry-a", date = 10, odometerKm = 100, inconsistent = 1)
            database.insertEntry("entry-b", date = 20, odometerKm = 300, inconsistent = 0)
            database.insertEntry("entry-c", date = 30, odometerKm = 200, inconsistent = 1)

            DatabaseMutations(database.database).tombstoneFuelEntriesForVehicle(
                vehicleId = "vehicle-1",
                deletedAt = 50,
                updatedAt = 50,
                syncState = "PENDING",
            )

            val rows =
                listOf("entry-a", "entry-b", "entry-c").map { id ->
                    database.database.databaseQueries
                        .selectFuelEntryById(id)
                        .awaitAsOne()
                }
            assertEquals(listOf(1L, 1L, 1L), rows.map { it.deleted })
            assertEquals(listOf(50L, 50L, 50L), rows.map { it.deletedAt })
            assertEquals(listOf(1L, 0L, 1L), rows.map { it.odometerInconsistent })
            assertEquals(listOf(2L, 3L, 4L), rows.map { it.localMutationSeq })
            assertEquals(100L, database.vehicleCurrentOdometer())
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
