package com.ruizurraca.carapp.feature.vehicle.data

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import com.ruizurraca.carapp.core.database.VehicleDatabaseAccess
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.FuelType
import com.ruizurraca.carapp.core.model.LOCAL_OWNER
import com.ruizurraca.carapp.core.model.OwnerId
import com.ruizurraca.carapp.core.testing.InMemoryDatabaseFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Instant

class SqlDelightVehicleLocalDataSourceTest {
    @Test
    fun failedWriteRollsBackAnInsertedVehicle() =
        runTest {
            val factory = InMemoryDatabaseFactory()
            val database = factory.create()
            val dataSource = SqlDelightVehicleLocalDataSource(VehicleDatabaseAccess(database))
            try {
                assertFailsWith<ExpectedFailure> {
                    dataSource.writeTransaction {
                        insertVehicle(localVehicle(), outboxPayload = null)
                        throw ExpectedFailure()
                    }
                }

                assertNull(database.databaseQueries.selectVehicleById(VEHICLE_ID).awaitAsOneOrNull())
            } finally {
                factory.close()
            }
        }

    @Test
    fun failedCascadePayloadRollsBackEveryTombstone() =
        runTest {
            withVehicleRepositoryTestScope(OwnerId("owner-a")) {
                seedVehicle()
                seedFuelEntry(FIRST_FUEL_ENTRY_ID, date = 1_100, odometerKm = 20)
                seedFuelEntry(SECOND_FUEL_ENTRY_ID, date = 1_200, odometerKm = 30)
                val dataSource = SqlDelightVehicleLocalDataSource(VehicleDatabaseAccess(database))

                assertFailsWith<CascadeFailure> {
                    dataSource.writeTransaction {
                        val local = requireNotNull(vehicle(OwnerId("owner-a"), EntityId(VEHICLE_ID)))
                        tombstoneVehicleCascade(
                            vehicle = local.copy(updatedAt = NOW, deletedAt = NOW),
                            vehicleOutboxPayload = "{}",
                            fuelEntryOutboxPayload = { entry ->
                                if (entry.id == SECOND_FUEL_ENTRY_ID) throw CascadeFailure()
                                "{}"
                            },
                        )
                    }
                }

                assertNull(requireNotNull(vehicle()).deletedAt)
                assertNull(requireNotNull(fuelEntry(FIRST_FUEL_ENTRY_ID)).deletedAt)
                assertNull(requireNotNull(fuelEntry(SECOND_FUEL_ENTRY_ID)).deletedAt)
            }
        }
}

private fun localVehicle(): LocalVehicle =
    LocalVehicle(
        id = EntityId(VEHICLE_ID),
        ownerId = LOCAL_OWNER,
        name = "Roadster",
        nameFold = "roadster",
        initialOdometerKm = 10,
        currentOdometerKm = 10,
        brand = null,
        model = null,
        fuelType = FuelType.GASOLINE,
        createdAt = Instant.fromEpochMilliseconds(CREATED_AT),
        updatedAt = NOW,
        serverUpdatedAt = null,
        deletedAt = null,
        syncState = "PENDING",
        localRevision = 1,
        localMutationSeq = 0,
        schemaVersion = 1,
    )

private class ExpectedFailure : RuntimeException()

private class CascadeFailure : RuntimeException()
