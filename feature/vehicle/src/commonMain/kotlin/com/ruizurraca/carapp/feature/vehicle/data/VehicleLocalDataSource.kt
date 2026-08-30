package com.ruizurraca.carapp.feature.vehicle.data

import com.ruizurraca.carapp.core.database.FuelEntryDatabaseRow
import com.ruizurraca.carapp.core.database.VehicleDatabaseAccess
import com.ruizurraca.carapp.core.database.VehicleDatabaseWriteScope
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.OwnerId
import com.ruizurraca.carapp.feature.vehicle.domain.VehicleNameCandidate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

internal interface VehicleLocalDataSource {
    fun observeVehicles(
        ownerId: OwnerId,
        includeDeleted: Boolean,
    ): Flow<List<LocalVehicle>>

    fun observeVehicle(
        ownerId: OwnerId,
        id: EntityId,
    ): Flow<LocalVehicle?>

    fun observeVehicleEditFacts(
        ownerId: OwnerId,
        id: EntityId,
    ): Flow<LocalVehicleEditFacts?>

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
        fuelEntryOutboxPayload: (FuelEntryDatabaseRow) -> String?,
    )
}

internal class SqlDelightVehicleLocalDataSource(
    private val databaseAccess: VehicleDatabaseAccess,
) : VehicleLocalDataSource {
    override fun observeVehicles(
        ownerId: OwnerId,
        includeDeleted: Boolean,
    ): Flow<List<LocalVehicle>> =
        databaseAccess
            .observeVehicles(ownerId.value, includeDeleted)
            .map { rows -> rows.map { it.toLocalVehicle() } }

    override fun observeVehicle(
        ownerId: OwnerId,
        id: EntityId,
    ): Flow<LocalVehicle?> =
        databaseAccess
            .observeVehicle(ownerId.value, id.value)
            .map { it?.toLocalVehicle() }

    override fun observeVehicleEditFacts(
        ownerId: OwnerId,
        id: EntityId,
    ): Flow<LocalVehicleEditFacts?> =
        combine(
            databaseAccess.observeVehicle(ownerId.value, id.value),
            databaseAccess.observeHasActiveFuelEntries(id.value),
        ) { vehicle, hasActiveFuelEntries ->
            vehicle?.let { row ->
                LocalVehicleEditFacts(
                    vehicle = row.toLocalVehicle(),
                    canEditInitialOdometer = !hasActiveFuelEntries,
                )
            }
        }

    override suspend fun <T> writeTransaction(block: suspend VehicleWriteScope.() -> T): T =
        databaseAccess.writeTransaction {
            SqlDelightVehicleWriteScope(this).block()
        }
}

internal data class LocalVehicleEditFacts(
    val vehicle: LocalVehicle,
    val canEditInitialOdometer: Boolean,
)

private class SqlDelightVehicleWriteScope(
    private val databaseScope: VehicleDatabaseWriteScope,
) : VehicleWriteScope {
    override suspend fun activeVehicleCandidates(ownerId: OwnerId): List<VehicleNameCandidate> =
        databaseScope
            .activeVehicles(ownerId.value)
            .map { VehicleNameCandidate(EntityId(it.id), it.name) }

    override suspend fun vehicle(
        ownerId: OwnerId,
        id: EntityId,
    ): LocalVehicle? =
        databaseScope
            .vehicle(ownerId.value, id.value)
            ?.toLocalVehicle()

    override suspend fun hasActiveFuelEntries(id: EntityId): Boolean = databaseScope.hasActiveFuelEntries(id.value)

    override suspend fun insertVehicle(
        vehicle: LocalVehicle,
        outboxPayload: String?,
    ) {
        databaseScope.insertVehicle(
            vehicle = vehicle.toDatabaseVehicle(),
            outboxPayload = outboxPayload,
        )
    }

    override suspend fun updateVehicle(
        vehicle: LocalVehicle,
        outboxPayload: String?,
    ) {
        databaseScope.updateVehicle(
            vehicle = vehicle.toDatabaseVehicle(),
            outboxPayload = outboxPayload,
        )
    }

    override suspend fun tombstoneVehicleCascade(
        vehicle: LocalVehicle,
        vehicleOutboxPayload: String?,
        fuelEntryOutboxPayload: (FuelEntryDatabaseRow) -> String?,
    ) {
        databaseScope.tombstoneVehicleCascade(
            vehicle = vehicle.toDatabaseVehicle(),
            vehicleOutboxPayload = vehicleOutboxPayload,
            fuelEntryOutboxPayload = fuelEntryOutboxPayload,
        )
    }
}
