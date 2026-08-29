package com.ruizurraca.carapp.core.database

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

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
        outboxPayload: String?,
    )

    suspend fun updateFuelEntry(
        entry: FuelEntryDatabaseRow,
        outboxPayload: String?,
    )

    suspend fun tombstoneFuelEntry(
        entry: FuelEntryDatabaseRow,
        outboxPayload: String?,
    )
}

/** E1-06 RED scaffold. SQLDelight behavior is introduced during GREEN. */
class FuelEntryDatabaseAccess(
    private val database: AppDatabase,
) {
    fun observeFuelEntryList(
        ownerId: String,
        vehicleId: String,
        includeDeleted: Boolean,
        limit: Long,
    ): Flow<List<FuelEntryDatabaseRow>> = flowOf(emptyList())

    fun observeConsumptionEntries(
        ownerId: String,
        vehicleId: String,
        limit: Long,
    ): Flow<List<FuelEntryDatabaseRow>> = flowOf(emptyList())

    suspend fun fuelEntry(
        ownerId: String,
        id: String,
    ): FuelEntryDatabaseRow? = null

    suspend fun <T> writeTransaction(block: suspend FuelEntryDatabaseWriteScope.() -> T): T =
        database.transactionWithResult(noEnclosing = true) { RedFuelEntryDatabaseWriteScope.block() }
}

private object RedFuelEntryDatabaseWriteScope : FuelEntryDatabaseWriteScope {
    override suspend fun vehicle(
        ownerId: String,
        id: String,
    ): FuelEntryVehicleDatabaseFacts? = null

    override suspend fun fuelEntry(
        ownerId: String,
        id: String,
    ): FuelEntryDatabaseRow? = null

    override suspend fun previousActiveFuelEntry(
        vehicleId: String,
        date: Long,
        createdAt: Long,
        id: String,
        excludedId: String?,
    ): FuelEntryDatabaseRow? = null

    override suspend fun insertFuelEntry(
        entry: FuelEntryDatabaseRow,
        outboxPayload: String?,
    ) = Unit

    override suspend fun updateFuelEntry(
        entry: FuelEntryDatabaseRow,
        outboxPayload: String?,
    ) = Unit

    override suspend fun tombstoneFuelEntry(
        entry: FuelEntryDatabaseRow,
        outboxPayload: String?,
    ) = Unit
}
