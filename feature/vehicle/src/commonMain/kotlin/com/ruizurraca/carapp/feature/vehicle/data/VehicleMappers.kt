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
        id = EntityId(id),
        ownerId = OwnerId(ownerId),
        name = name,
        nameFold = nameFold,
        initialOdometerKm = initialOdometerKm,
        currentOdometerKm = currentOdometerKm,
        brand = brand,
        model = model,
        fuelType = FuelType.valueOf(fuelType),
        createdAt = Instant.fromEpochMilliseconds(createdAt),
        updatedAt = Instant.fromEpochMilliseconds(updatedAt),
        serverUpdatedAt = serverUpdatedAt?.let(Instant::fromEpochMilliseconds),
        deletedAt = deletedAt?.let(Instant::fromEpochMilliseconds),
        syncState = syncState,
        localRevision = localRevision,
        localMutationSeq = localMutationSeq,
        schemaVersion = schemaVersion,
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
