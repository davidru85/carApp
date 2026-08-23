package com.ruizurraca.carapp.core.database

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DatabaseReadQueriesTest {
    private lateinit var testDatabase: TestDatabase

    @AfterTest
    fun tearDown() {
        if (::testDatabase.isInitialized) testDatabase.close()
    }

    @Test
    fun vehicleQueryEmitsAFlowUpdateAfterInsert() =
        runTest {
            val queries = createDatabase().database.databaseQueries

            queries.observeVehicles().test {
                assertEquals(emptyList(), awaitItem())

                queries.insertVehicleForReadTest(vehicleId = "vehicle-1", vehicleName = "Car")

                assertEquals(listOf("vehicle-1"), awaitItem().map(Vehicle::id))
            }
        }

    @Test
    fun vehicleByIdIsAOneShotSuspendingQuery() =
        runTest {
            val queries = createDatabase().database.databaseQueries
            queries.insertVehicleForReadTest(vehicleId = "vehicle-1", vehicleName = "Car")

            assertEquals("Car", queries.vehicleById("vehicle-1")?.name)
        }

    private fun createDatabase(): TestDatabase = TestDatabase.create().also { testDatabase = it }
}

private suspend fun DatabaseQueries.insertVehicleForReadTest(
    vehicleId: String,
    vehicleName: String,
) {
    insertVehicleRow(
        id = vehicleId,
        ownerId = "LOCAL_OWNER",
        name = vehicleName,
        nameFold = vehicleName.lowercase(),
        initialOdometerKm = 0,
        currentOdometerKm = 0,
        brand = null,
        model = null,
        fuelType = "GASOLINE",
        createdAt = 1,
        updatedAt = 1,
        serverUpdatedAt = null,
        deleted = 0,
        deletedAt = null,
        syncState = "PENDING",
        localRevision = 1,
        localMutationSeq = 1,
        schemaVersion = 1,
    )
}
