package com.ruizurraca.carapp.core.database

import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FuelEntryUpdateRecomputeTest {
    private lateinit var testDatabase: TestDatabase

    @AfterTest
    fun tearDown() {
        if (::testDatabase.isInitialized) testDatabase.close()
    }

    @Test
    fun chronologicalMoveRecomputesUpdatedPreSuccessorAndPostSuccessorOnly() =
        runTest {
            val database = createDatabaseWithVehicle()
            database.insertEntry("entry-a", date = 10, odometerKm = 100, inconsistent = 1)
            database.insertEntry("entry-b", date = 20, odometerKm = 200, inconsistent = 1)
            database.insertEntry("entry-c", date = 30, odometerKm = 150, inconsistent = 1)
            database.insertEntry("entry-d", date = 40, odometerKm = 300, inconsistent = 1)

            database.updateFuelEntryForMutationTest(existingId = "entry-b", date = 35)

            assertEquals(1L, database.fuelEntryInconsistent("entry-a"))
            assertEquals(0L, database.fuelEntryInconsistent("entry-b"))
            assertEquals(0L, database.fuelEntryInconsistent("entry-c"))
            assertEquals(0L, database.fuelEntryInconsistent("entry-d"))
            assertEquals(300L, database.vehicleCurrentOdometer())
        }

    @Test
    fun odometerOnlyUpdateRecomputesTheCoincidentSuccessorOnce() =
        runTest {
            val database = createDatabaseWithVehicle()
            database.insertEntry("entry-a", date = 10, odometerKm = 100, inconsistent = 1)
            database.insertEntry("entry-b", date = 20, odometerKm = 200, inconsistent = 1)
            database.insertEntry("entry-c", date = 30, odometerKm = 150, inconsistent = 0)

            database.updateFuelEntryForMutationTest(existingId = "entry-b", odometerKm = 300)

            assertEquals(1L, database.fuelEntryInconsistent("entry-a"))
            assertEquals(0L, database.fuelEntryInconsistent("entry-b"))
            assertEquals(1L, database.fuelEntryInconsistent("entry-c"))
            assertEquals(300L, database.vehicleCurrentOdometer())
        }

    @Test
    fun notesAndCurrencyUpdateDoesNotRecomputeOdometerFlags() =
        runTest {
            val database = createDatabaseWithVehicle()
            database.insertEntry("entry-a", date = 10, odometerKm = 100, inconsistent = 1)
            database.insertEntry("entry-b", date = 20, odometerKm = 200, inconsistent = 1)

            database.updateFuelEntryForMutationTest(
                existingId = "entry-b",
                currency = "USD",
                notes = "updated",
            )

            assertEquals(1L, database.fuelEntryInconsistent("entry-a"))
            assertEquals(1L, database.fuelEntryInconsistent("entry-b"))
            assertEquals(200L, database.vehicleCurrentOdometer())
        }

    private suspend fun createDatabaseWithVehicle(): TestDatabase =
        createDatabase().also { it.insertVehicleForMutationTest(currentOdometerKm = 999) }

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
