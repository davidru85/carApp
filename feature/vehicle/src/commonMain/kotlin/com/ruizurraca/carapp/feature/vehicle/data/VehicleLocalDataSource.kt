package com.ruizurraca.carapp.feature.vehicle.data

import com.ruizurraca.carapp.core.database.AppDatabase
import com.ruizurraca.carapp.core.database.DatabaseMutations
import com.ruizurraca.carapp.core.database.Fuel_entry
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.OwnerId
import com.ruizurraca.carapp.feature.vehicle.domain.VehicleNameCandidate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

internal interface VehicleLocalDataSource {
    fun observeVehicles(
        ownerId: OwnerId,
        includeDeleted: Boolean,
    ): Flow<List<LocalVehicle>>

    fun observeVehicle(
        ownerId: OwnerId,
        id: EntityId,
    ): Flow<LocalVehicle?>

    suspend fun <T> writeTransaction(block: suspend VehicleWriteScope.() -> T): T
}

internal interface VehicleWriteScope {
    suspend fun activeVehicleCandidates(ownerId: OwnerId): List<VehicleNameCandidate>

    suspend fun vehicle(
        ownerId: OwnerId,
        id: EntityId,
    ): LocalVehicle?

    suspend fun hasActiveFuelEntries(id: EntityId): Boolean

    suspend fun insertVehicle(
        vehicle: LocalVehicle,
        outboxPayload: String?,
    )

    suspend fun updateVehicle(
        vehicle: LocalVehicle,
        outboxPayload: String?,
    )

    suspend fun tombstoneVehicleCascade(
        vehicle: LocalVehicle,
        vehicleOutboxPayload: String?,
        fuelEntryOutboxPayload: (Fuel_entry) -> String?,
    )
}

internal class SqlDelightVehicleLocalDataSource(
    database: AppDatabase,
) : VehicleLocalDataSource {
    private val mutations = DatabaseMutations(database)
    private val scope = RedVehicleWriteScope(mutations)

    override fun observeVehicles(
        ownerId: OwnerId,
        includeDeleted: Boolean,
    ): Flow<List<LocalVehicle>> = flowOf(emptyList())

    override fun observeVehicle(
        ownerId: OwnerId,
        id: EntityId,
    ): Flow<LocalVehicle?> = flowOf(null)

    override suspend fun <T> writeTransaction(block: suspend VehicleWriteScope.() -> T): T = scope.block()
}

private class RedVehicleWriteScope(
    private val mutations: DatabaseMutations,
) : VehicleWriteScope {
    override suspend fun activeVehicleCandidates(ownerId: OwnerId): List<VehicleNameCandidate> = emptyList()

    override suspend fun vehicle(
        ownerId: OwnerId,
        id: EntityId,
    ): LocalVehicle? = null

    override suspend fun hasActiveFuelEntries(id: EntityId): Boolean = false

    override suspend fun insertVehicle(
        vehicle: LocalVehicle,
        outboxPayload: String?,
    ) {
        mutations.insertVehicle(
            id = vehicle.id.value,
            ownerId = vehicle.ownerId.value,
            name = vehicle.name,
            nameFold = vehicle.nameFold,
            initialOdometerKm = vehicle.initialOdometerKm,
            brand = vehicle.brand,
            model = vehicle.model,
            fuelType = vehicle.fuelType.name,
            createdAt = vehicle.createdAt.toEpochMilliseconds(),
            updatedAt = vehicle.updatedAt.toEpochMilliseconds(),
            schemaVersion = vehicle.schemaVersion,
            outboxPayload = outboxPayload,
        )
    }

    override suspend fun updateVehicle(
        vehicle: LocalVehicle,
        outboxPayload: String?,
    ) = Unit

    override suspend fun tombstoneVehicleCascade(
        vehicle: LocalVehicle,
        vehicleOutboxPayload: String?,
        fuelEntryOutboxPayload: (Fuel_entry) -> String?,
    ) = Unit
}
