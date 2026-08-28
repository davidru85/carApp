package com.ruizurraca.carapp.feature.vehicle.data

import com.ruizurraca.carapp.core.database.Vehicle as DatabaseVehicle
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.FuelType
import com.ruizurraca.carapp.core.model.OwnerId
import com.ruizurraca.carapp.core.model.Vehicle
import kotlin.time.Instant

internal data class LocalVehicle(
    val id: EntityId,
    val ownerId: OwnerId,
    val name: String,
    val nameFold: String,
    val initialOdometerKm: Long,
    val currentOdometerKm: Long,
    val brand: String?,
    val model: String?,
    val fuelType: FuelType,
    val createdAt: Instant,
    val updatedAt: Instant,
    val serverUpdatedAt: Instant?,
    val deletedAt: Instant?,
    val syncState: String,
    val localRevision: Long,
    val localMutationSeq: Long,
    val schemaVersion: Long,
)

internal fun DatabaseVehicle.toLocalVehicle(): LocalVehicle =
    LocalVehicle(
        id = EntityId(""),
        ownerId = OwnerId(""),
        name = "",
        nameFold = "",
        initialOdometerKm = 0,
        currentOdometerKm = 0,
        brand = null,
        model = null,
        fuelType = FuelType.GASOLINE,
        createdAt = Instant.fromEpochMilliseconds(0),
        updatedAt = Instant.fromEpochMilliseconds(0),
        serverUpdatedAt = null,
        deletedAt = null,
        syncState = "PENDING",
        localRevision = 0,
        localMutationSeq = 0,
        schemaVersion = 0,
    )

internal fun LocalVehicle.toDatabaseVehicle(): DatabaseVehicle =
    DatabaseVehicle(
        id = id.value,
        ownerId = ownerId.value,
        name = name,
        nameFold = nameFold,
        initialOdometerKm = initialOdometerKm,
        currentOdometerKm = currentOdometerKm,
        brand = brand,
        model = model,
        fuelType = fuelType.name,
        createdAt = createdAt.toEpochMilliseconds(),
        updatedAt = updatedAt.toEpochMilliseconds(),
        serverUpdatedAt = serverUpdatedAt?.toEpochMilliseconds(),
        deleted = if (deletedAt == null) 0 else 1,
        deletedAt = deletedAt?.toEpochMilliseconds(),
        syncState = syncState,
        localRevision = localRevision,
        localMutationSeq = localMutationSeq,
        schemaVersion = schemaVersion,
    )

internal fun LocalVehicle.toDomainVehicle(): Vehicle =
    Vehicle(
        id = id,
        ownerId = ownerId,
        name = name,
        initialOdometerKm = initialOdometerKm,
        currentOdometerKm = currentOdometerKm,
        brand = brand,
        model = model,
        fuelType = fuelType,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
    )
