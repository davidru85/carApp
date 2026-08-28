package com.ruizurraca.carapp.feature.vehicle.data

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.ruizurraca.carapp.core.database.AppDatabase
import com.ruizurraca.carapp.core.database.DatabaseMutations
import com.ruizurraca.carapp.core.database.DatabaseQueries
import com.ruizurraca.carapp.core.database.Fuel_entry
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.OwnerId
import com.ruizurraca.carapp.feature.vehicle.domain.VehicleNameCandidate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.coroutines.EmptyCoroutineContext

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
    private val database: AppDatabase,
) : VehicleLocalDataSource {
    private val queries: DatabaseQueries = database.databaseQueries
    private val mutations = DatabaseMutations(database)
    private val scope = SqlDelightVehicleWriteScope(queries, mutations)

    override fun observeVehicles(
        ownerId: OwnerId,
        includeDeleted: Boolean,
    ): Flow<List<LocalVehicle>> =
        queries
            .selectVehiclesByOwner(ownerId.value, if (includeDeleted) 1 else 0)
            .asFlow()
            .mapToList(EmptyCoroutineContext)
            .map { rows -> rows.map { it.toLocalVehicle() } }

    override fun observeVehicle(
        ownerId: OwnerId,
        id: EntityId,
    ): Flow<LocalVehicle?> =
        queries
            .selectVehicleByOwnerAndId(ownerId.value, id.value)
            .asFlow()
            .mapToOneOrNull(EmptyCoroutineContext)
            .map { it?.toLocalVehicle() }

    override suspend fun <T> writeTransaction(block: suspend VehicleWriteScope.() -> T): T =
        database.transactionWithResult(noEnclosing = true) { scope.block() }
}

private class SqlDelightVehicleWriteScope(
    private val queries: DatabaseQueries,
    private val mutations: DatabaseMutations,
) : VehicleWriteScope {
    override suspend fun activeVehicleCandidates(ownerId: OwnerId): List<VehicleNameCandidate> =
        queries
            .selectVehiclesByOwner(ownerId.value, includeDeleted = 0)
            .awaitAsList()
            .map { VehicleNameCandidate(EntityId(it.id), it.name) }

    override suspend fun vehicle(
        ownerId: OwnerId,
        id: EntityId,
    ): LocalVehicle? =
        queries
            .selectVehicleByOwnerAndId(ownerId.value, id.value)
            .awaitAsOneOrNull()
            ?.toLocalVehicle()

    override suspend fun hasActiveFuelEntries(id: EntityId): Boolean =
        queries.countActiveFuelEntriesByVehicle(id.value).awaitAsOne() > 0

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
    ) {
        mutations.updateVehicle(
            id = vehicle.id.value,
            ownerId = vehicle.ownerId.value,
            name = vehicle.name,
            nameFold = vehicle.nameFold,
            initialOdometerKm = vehicle.initialOdometerKm,
            brand = vehicle.brand,
            model = vehicle.model,
            fuelType = vehicle.fuelType.name,
            updatedAt = vehicle.updatedAt.toEpochMilliseconds(),
            outboxPayload = outboxPayload,
        )
    }

    override suspend fun tombstoneVehicleCascade(
        vehicle: LocalVehicle,
        vehicleOutboxPayload: String?,
        fuelEntryOutboxPayload: (Fuel_entry) -> String?,
    ) {
        mutations.tombstoneVehicleWithFuelEntries(
            id = vehicle.id.value,
            ownerId = vehicle.ownerId.value,
            deletedAt = requireNotNull(vehicle.deletedAt).toEpochMilliseconds(),
            updatedAt = vehicle.updatedAt.toEpochMilliseconds(),
            vehicleOutboxPayload = vehicleOutboxPayload,
            fuelEntryOutboxPayload = fuelEntryOutboxPayload,
        )
    }
}
