package com.ruizurraca.carapp.core.database

import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FuelEntryCreateRecomputeTest {
    private lateinit var testDatabase: TestDatabase

    @AfterTest
    fun tearDown() {
        if (::testDatabase.isInitialized) testDatabase.close()
    }

    @Test
    fun createRecomputesInsertedEntrySuccessorAndVehicleMaximum() =
        runTest {
            val database = createDatabase()
            database.insertVehicleForMutationTest(currentOdometerKm = 999)
            database.insertFuelEntryForMutationTest(
                id = "entry-a",
                date = 1,
                createdAt = 1,
                odometerKm = 200,
            )
            database.insertFuelEntryForMutationTest(
                id = "entry-c",
                date = 3,
                createdAt = 3,
                odometerKm = 250,
                odometerInconsistent = 1,
            )

            DatabaseMutations(database.database).insertFuelEntryForTest(
                id = "entry-b",
                date = 2,
                createdAt = 2,
                odometerKm = 150,
            )

            assertEquals(1L, database.fuelEntryInconsistent("entry-b"))
            assertEquals(0L, database.fuelEntryInconsistent("entry-c"))
            assertEquals(250L, database.vehicleCurrentOdometer())
        }

    private fun createDatabase(): TestDatabase = TestDatabase.create().also { testDatabase = it }
}
