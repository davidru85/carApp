package com.ruizurraca.carapp.feature.vehicle.data

import com.ruizurraca.carapp.core.database.Vehicle as DatabaseVehicle
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.FuelType
import com.ruizurraca.carapp.core.model.OwnerId
import com.ruizurraca.carapp.core.model.Vehicle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class VehicleMappersTest {
    @Test
    fun databaseTombstoneRoundTripsEveryLocalField() {
        val row = databaseVehicle(deletedAt = 3_000)

        assertEquals(row, row.toLocalVehicle().toDatabaseVehicle())
    }

    @Test
    fun localVehicleMapsCanonicalFieldsToTheDomainModel() {
        val row = databaseVehicle(deletedAt = null)

        assertEquals(
            Vehicle(
                id = EntityId(VEHICLE_ID),
                ownerId = OwnerId("owner-a"),
                name = "Roadster",
                initialOdometerKm = 10,
                currentOdometerKm = 20,
                brand = "Acme",
                model = "One",
                fuelType = FuelType.DIESEL,
                createdAt = Instant.fromEpochMilliseconds(1_000),
                updatedAt = Instant.fromEpochMilliseconds(2_000),
                deletedAt = null,
            ),
            row.toLocalVehicle().toDomainVehicle(),
        )
    }
}

private fun databaseVehicle(deletedAt: Long?): DatabaseVehicle =
    DatabaseVehicle(
        id = VEHICLE_ID,
        ownerId = "owner-a",
        name = "Roadster",
        nameFold = "roadster",
        initialOdometerKm = 10,
        currentOdometerKm = 20,
        brand = "Acme",
        model = "One",
        fuelType = "DIESEL",
        createdAt = 1_000,
        updatedAt = 2_000,
        serverUpdatedAt = 2_500,
        deleted = if (deletedAt == null) 0 else 1,
        deletedAt = deletedAt,
        syncState = "FAILED_RETRYABLE",
        localRevision = 4,
        localMutationSeq = 9,
        schemaVersion = 1,
    )
