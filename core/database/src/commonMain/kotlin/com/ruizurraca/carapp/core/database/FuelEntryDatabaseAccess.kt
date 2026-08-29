package com.ruizurraca.carapp.core.database

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.coroutines.EmptyCoroutineContext

data class FuelEntryVehicleDatabaseFacts(
    val id: String,
    val initialOdometerKm: Long,
    val createdAt: Long,
    val deletedAt: Long?,
)

interface FuelEntryDatabaseWriteScope {
    suspend fun vehicle(
        ownerId: String,
        id: String,
    ): FuelEntryVehicleDatabaseFacts?

    suspend fun fuelEntry(
        ownerId: String,
        id: String,
    ): FuelEntryDatabaseRow?

    suspend fun previousActiveFuelEntry(
        vehicleId: String,
        date: Long,
        createdAt: Long,
        id: String,
        excludedId: String?,
    ): FuelEntryDatabaseRow?

    suspend fun insertFuelEntry(
        entry: FuelEntryDatabaseRow,
        outboxPayload: (FuelEntryDatabaseRow) -> String?,
    )

    suspend fun updateFuelEntry(
        entry: FuelEntryDatabaseRow,
        outboxPayload: (FuelEntryDatabaseRow) -> String?,
    )

    suspend fun tombstoneFuelEntry(
        entry: FuelEntryDatabaseRow,
        outboxPayload: (FuelEntryDatabaseRow) -> String?,
    )
}

/** Keeps SQLDelight-generated types and transaction ownership inside `:core:database`. */
class FuelEntryDatabaseAccess(
    private val database: AppDatabase,
) {
    private val queries = database.databaseQueries
    private val writeScope = SqlDelightFuelEntryDatabaseWriteScope(queries, DatabaseMutations(database))

    fun observeFuelEntryList(
        ownerId: String,
        vehicleId: String,
        includeDeleted: Boolean,
        limit: Long,
    ): Flow<List<FuelEntryDatabaseRow>> =
        queries
            .selectFuelEntriesForList(ownerId, vehicleId, if (includeDeleted) 1L else 0L, limit)
            .asFlow()
            .mapToList(EmptyCoroutineContext)
            .map { rows -> rows.map(Fuel_entry::toFuelEntryDatabaseRow) }

    fun observeConsumptionEntries(
        ownerId: String,
        vehicleId: String,
        limit: Long,
    ): Flow<List<FuelEntryDatabaseRow>> =
        queries
            .selectFuelEntriesForConsumption(ownerId, vehicleId, limit)
            .asFlow()
            .mapToList(EmptyCoroutineContext)
            .map { rows -> rows.map(Fuel_entry::toFuelEntryDatabaseRow) }

    suspend fun fuelEntry(
        ownerId: String,
        id: String,
    ): FuelEntryDatabaseRow? =
        queries
            .selectFuelEntryByOwnerAndId(ownerId, id)
            .awaitAsOneOrNull()
            ?.toFuelEntryDatabaseRow()

    suspend fun <T> writeTransaction(block: suspend FuelEntryDatabaseWriteScope.() -> T): T =
        database.transactionWithResult(noEnclosing = true) { writeScope.block() }
}

private class SqlDelightFuelEntryDatabaseWriteScope(
    private val queries: DatabaseQueries,
    private val mutations: DatabaseMutations,
) : FuelEntryDatabaseWriteScope {
    override suspend fun vehicle(
        ownerId: String,
        id: String,
    ): FuelEntryVehicleDatabaseFacts? =
        queries.selectVehicleByOwnerAndId(ownerId, id).awaitAsOneOrNull()?.let {
            FuelEntryVehicleDatabaseFacts(
                id = it.id,
                initialOdometerKm = it.initialOdometerKm,
                createdAt = it.createdAt,
                deletedAt = it.deletedAt,
            )
        }

    override suspend fun fuelEntry(
        ownerId: String,
        id: String,
    ): FuelEntryDatabaseRow? =
        queries
            .selectFuelEntryByOwnerAndId(ownerId, id)
            .awaitAsOneOrNull()
            ?.toFuelEntryDatabaseRow()

    override suspend fun previousActiveFuelEntry(
        vehicleId: String,
        date: Long,
        createdAt: Long,
        id: String,
        excludedId: String?,
    ): FuelEntryDatabaseRow? =
        queries
            .selectPreviousActiveFuelEntry(vehicleId, excludedId, date, createdAt, id)
            .awaitAsOneOrNull()
            ?.toFuelEntryDatabaseRow()

    override suspend fun insertFuelEntry(
        entry: FuelEntryDatabaseRow,
        outboxPayload: (FuelEntryDatabaseRow) -> String?,
    ) = mutations.insertLocalFuelEntry(entry, outboxPayload)

    override suspend fun updateFuelEntry(
        entry: FuelEntryDatabaseRow,
        outboxPayload: (FuelEntryDatabaseRow) -> String?,
    ) = mutations.updateLocalFuelEntry(entry, outboxPayload)

    override suspend fun tombstoneFuelEntry(
        entry: FuelEntryDatabaseRow,
        outboxPayload: (FuelEntryDatabaseRow) -> String?,
    ) = mutations.tombstoneLocalFuelEntry(entry, outboxPayload)
}
