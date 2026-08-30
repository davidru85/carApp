package com.ruizurraca.carapp.core.database

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.coroutines.EmptyCoroutineContext

@Suppress("LongParameterList")
data class VehicleDatabaseRow(
    val id: String,
    val ownerId: String,
    val name: String,
    val nameFold: String,
    val initialOdometerKm: Long,
    val currentOdometerKm: Long,
    val brand: String?,
    val model: String?,
    val fuelType: String,
    val createdAt: Long,
    val updatedAt: Long,
    val serverUpdatedAt: Long?,
    val deletedAt: Long?,
    val syncState: String,
    val localRevision: Long,
    val localMutationSeq: Long,
    val schemaVersion: Long,
)

@Suppress("LongParameterList")
data class FuelEntryDatabaseRow(
    val id: String,
    val ownerId: String,
    val vehicleId: String,
    val date: Long,
    val odometerKm: Long,
    val litersScaled: Long,
    val pricePerLiterScaled: Long,
    val totalCostMinor: Long,
    val currency: String,
    val isFullTank: Boolean,
    val hasMissedEntries: Boolean,
    val odometerInconsistent: Boolean,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val serverUpdatedAt: Long?,
    val deletedAt: Long?,
    val syncState: String,
    val localRevision: Long,
    val localMutationSeq: Long,
    val schemaVersion: Long,
)

interface VehicleDatabaseWriteScope {
    suspend fun activeVehicles(ownerId: String): List<VehicleDatabaseRow>

    suspend fun vehicle(
        ownerId: String,
        id: String,
    ): VehicleDatabaseRow?

    suspend fun hasActiveFuelEntries(id: String): Boolean

    suspend fun insertVehicle(
        vehicle: VehicleDatabaseRow,
        outboxPayload: String?,
    )

    suspend fun updateVehicle(
        vehicle: VehicleDatabaseRow,
        outboxPayload: String?,
    )

    suspend fun tombstoneVehicleCascade(
        vehicle: VehicleDatabaseRow,
        vehicleOutboxPayload: String?,
        fuelEntryOutboxPayload: (FuelEntryDatabaseRow) -> String?,
    )
}

/** Keeps SQLDelight-generated types and transaction ownership inside `:core:database`. */
class VehicleDatabaseAccess(
    private val database: AppDatabase,
) {
    private val queries = database.databaseQueries
    private val writeScope = SqlDelightVehicleDatabaseWriteScope(queries, DatabaseMutations(database))

    fun observeVehicles(
        ownerId: String,
        includeDeleted: Boolean,
    ): Flow<List<VehicleDatabaseRow>> =
        queries
            .selectVehiclesByOwner(ownerId, if (includeDeleted) 1L else 0L)
            .asFlow()
            .mapToList(EmptyCoroutineContext)
            .map { rows -> rows.map(Vehicle::toVehicleDatabaseRow) }

    fun observeVehicle(
        ownerId: String,
        id: String,
    ): Flow<VehicleDatabaseRow?> =
        queries
            .selectVehicleByOwnerAndId(ownerId, id)
            .asFlow()
            .mapToOneOrNull(EmptyCoroutineContext)
            .map { it?.toVehicleDatabaseRow() }

    fun observeHasActiveFuelEntries(id: String): Flow<Boolean> =
        queries
            .countActiveFuelEntriesByVehicle(id)
            .asFlow()
            .mapToOne(EmptyCoroutineContext)
            .map { count -> count > 0 }

    suspend fun <T> writeTransaction(block: suspend VehicleDatabaseWriteScope.() -> T): T =
        database.transactionWithResult(noEnclosing = true) { writeScope.block() }
}

private class SqlDelightVehicleDatabaseWriteScope(
    private val queries: DatabaseQueries,
    private val mutations: DatabaseMutations,
) : VehicleDatabaseWriteScope {
    override suspend fun activeVehicles(ownerId: String): List<VehicleDatabaseRow> =
        queries
            .selectVehiclesByOwner(ownerId, includeDeleted = 0L)
            .awaitAsList()
            .map(Vehicle::toVehicleDatabaseRow)

    override suspend fun vehicle(
        ownerId: String,
        id: String,
    ): VehicleDatabaseRow? =
        queries
            .selectVehicleByOwnerAndId(ownerId, id)
            .awaitAsOneOrNull()
            ?.toVehicleDatabaseRow()

    override suspend fun hasActiveFuelEntries(id: String): Boolean =
        queries.countActiveFuelEntriesByVehicle(id).awaitAsOne() > 0

    override suspend fun insertVehicle(
        vehicle: VehicleDatabaseRow,
        outboxPayload: String?,
    ) {
        mutations.insertVehicle(
            id = vehicle.id,
            ownerId = vehicle.ownerId,
            name = vehicle.name,
            nameFold = vehicle.nameFold,
            initialOdometerKm = vehicle.initialOdometerKm,
            brand = vehicle.brand,
            model = vehicle.model,
            fuelType = vehicle.fuelType,
            createdAt = vehicle.createdAt,
            updatedAt = vehicle.updatedAt,
            schemaVersion = vehicle.schemaVersion,
            outboxPayload = outboxPayload,
        )
    }

    override suspend fun updateVehicle(
        vehicle: VehicleDatabaseRow,
        outboxPayload: String?,
    ) {
        mutations.updateVehicle(
            id = vehicle.id,
            ownerId = vehicle.ownerId,
            name = vehicle.name,
            nameFold = vehicle.nameFold,
            initialOdometerKm = vehicle.initialOdometerKm,
            brand = vehicle.brand,
            model = vehicle.model,
            fuelType = vehicle.fuelType,
            updatedAt = vehicle.updatedAt,
            outboxPayload = outboxPayload,
        )
    }

    override suspend fun tombstoneVehicleCascade(
        vehicle: VehicleDatabaseRow,
        vehicleOutboxPayload: String?,
        fuelEntryOutboxPayload: (FuelEntryDatabaseRow) -> String?,
    ) {
        mutations.tombstoneVehicleWithFuelEntries(
            id = vehicle.id,
            ownerId = vehicle.ownerId,
            deletedAt = requireNotNull(vehicle.deletedAt),
            updatedAt = vehicle.updatedAt,
            vehicleOutboxPayload = vehicleOutboxPayload,
            fuelEntryOutboxPayload = fuelEntryOutboxPayload,
        )
    }
}

internal fun Vehicle.toVehicleDatabaseRow(): VehicleDatabaseRow =
    VehicleDatabaseRow(
        id = id,
        ownerId = ownerId,
        name = name,
        nameFold = nameFold,
        initialOdometerKm = initialOdometerKm,
        currentOdometerKm = currentOdometerKm,
        brand = brand,
        model = model,
        fuelType = fuelType,
        createdAt = createdAt,
        updatedAt = updatedAt,
        serverUpdatedAt = serverUpdatedAt,
        deletedAt = deletedAt,
        syncState = syncState,
        localRevision = localRevision,
        localMutationSeq = localMutationSeq,
        schemaVersion = schemaVersion,
    )

internal fun Fuel_entry.toFuelEntryDatabaseRow(): FuelEntryDatabaseRow =
    FuelEntryDatabaseRow(
        id = id,
        ownerId = ownerId,
        vehicleId = vehicleId,
        date = date,
        odometerKm = odometerKm,
        litersScaled = litersScaled,
        pricePerLiterScaled = pricePerLiterScaled,
        totalCostMinor = totalCostMinor,
        currency = currency,
        isFullTank = isFullTank != 0L,
        hasMissedEntries = hasMissedEntries != 0L,
        odometerInconsistent = odometerInconsistent != 0L,
        notes = notes,
        createdAt = createdAt,
        updatedAt = updatedAt,
        serverUpdatedAt = serverUpdatedAt,
        deletedAt = deletedAt,
        syncState = syncState,
        localRevision = localRevision,
        localMutationSeq = localMutationSeq,
        schemaVersion = schemaVersion,
    )
