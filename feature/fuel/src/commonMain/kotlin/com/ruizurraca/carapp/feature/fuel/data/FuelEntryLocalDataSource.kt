package com.ruizurraca.carapp.feature.fuel.data

import com.ruizurraca.carapp.core.common.MAX_ENTRIES_IN_MEMORY
import com.ruizurraca.carapp.core.database.FuelEntryDatabaseAccess
import com.ruizurraca.carapp.core.database.FuelEntryDatabaseWriteScope
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.OwnerId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

internal data class FuelEntryVehicleFacts(
    val id: EntityId,
    val initialOdometerKm: Long,
    val createdAt: Instant,
    val deletedAt: Instant?,
)

internal interface FuelEntryLocalDataSource {
    fun observeFuelEntryList(
        ownerId: OwnerId,
        vehicleId: EntityId,
        includeDeleted: Boolean,
    ): Flow<List<LocalFuelEntry>>

    fun observeConsumptionEntries(
        ownerId: OwnerId,
        vehicleId: EntityId,
    ): Flow<List<LocalFuelEntry>>

    suspend fun fuelEntry(
        ownerId: OwnerId,
        id: EntityId,
    ): LocalFuelEntry?

    suspend fun <T> writeTransaction(block: suspend FuelEntryWriteScope.() -> T): T
}

internal interface FuelEntryWriteScope {
    suspend fun vehicle(
        ownerId: OwnerId,
        id: EntityId,
    ): FuelEntryVehicleFacts?

    suspend fun fuelEntry(
        ownerId: OwnerId,
        id: EntityId,
    ): LocalFuelEntry?

    suspend fun previousActiveFuelEntry(
        vehicleId: EntityId,
        date: Instant,
        createdAt: Instant,
        id: EntityId,
        excludedId: EntityId?,
    ): LocalFuelEntry?

    suspend fun insertFuelEntry(
        entry: LocalFuelEntry,
        outboxPayload: (LocalFuelEntry) -> String?,
    )

    suspend fun updateFuelEntry(
        entry: LocalFuelEntry,
        outboxPayload: (LocalFuelEntry) -> String?,
    )

    suspend fun tombstoneFuelEntry(
        entry: LocalFuelEntry,
        outboxPayload: (LocalFuelEntry) -> String?,
    )
}

internal class SqlDelightFuelEntryLocalDataSource(
    private val databaseAccess: FuelEntryDatabaseAccess,
    private val rowLimit: Long = MAX_ENTRIES_IN_MEMORY.toLong(),
) : FuelEntryLocalDataSource {
    override fun observeFuelEntryList(
        ownerId: OwnerId,
        vehicleId: EntityId,
        includeDeleted: Boolean,
    ): Flow<List<LocalFuelEntry>> =
        databaseAccess
            .observeFuelEntryList(
                ownerId.value,
                vehicleId.value,
                includeDeleted,
                rowLimit,
            ).map { rows -> rows.map { it.toLocalFuelEntry() } }

    override fun observeConsumptionEntries(
        ownerId: OwnerId,
        vehicleId: EntityId,
    ): Flow<List<LocalFuelEntry>> =
        databaseAccess
            .observeConsumptionEntries(ownerId.value, vehicleId.value, rowLimit)
            .map { rows -> rows.map { it.toLocalFuelEntry() } }

    override suspend fun fuelEntry(
        ownerId: OwnerId,
        id: EntityId,
    ): LocalFuelEntry? = databaseAccess.fuelEntry(ownerId.value, id.value)?.toLocalFuelEntry()

    override suspend fun <T> writeTransaction(block: suspend FuelEntryWriteScope.() -> T): T =
        databaseAccess.writeTransaction { SqlDelightFuelEntryWriteScope(this).block() }
}

private class SqlDelightFuelEntryWriteScope(
    private val databaseScope: FuelEntryDatabaseWriteScope,
) : FuelEntryWriteScope {
    override suspend fun vehicle(
        ownerId: OwnerId,
        id: EntityId,
    ): FuelEntryVehicleFacts? =
        databaseScope.vehicle(ownerId.value, id.value)?.let {
            FuelEntryVehicleFacts(
                id = EntityId(it.id),
                initialOdometerKm = it.initialOdometerKm,
                createdAt = Instant.fromEpochMilliseconds(it.createdAt),
                deletedAt = it.deletedAt?.let(Instant::fromEpochMilliseconds),
            )
        }

    override suspend fun fuelEntry(
        ownerId: OwnerId,
        id: EntityId,
    ): LocalFuelEntry? = databaseScope.fuelEntry(ownerId.value, id.value)?.toLocalFuelEntry()

    override suspend fun previousActiveFuelEntry(
        vehicleId: EntityId,
        date: Instant,
        createdAt: Instant,
        id: EntityId,
        excludedId: EntityId?,
    ): LocalFuelEntry? =
        databaseScope
            .previousActiveFuelEntry(
                vehicleId = vehicleId.value,
                date = date.toEpochMilliseconds(),
                createdAt = createdAt.toEpochMilliseconds(),
                id = id.value,
                excludedId = excludedId?.value,
            )?.toLocalFuelEntry()

    override suspend fun insertFuelEntry(
        entry: LocalFuelEntry,
        outboxPayload: (LocalFuelEntry) -> String?,
    ) = databaseScope.insertFuelEntry(entry.toDatabaseFuelEntry()) { outboxPayload(it.toLocalFuelEntry()) }

    override suspend fun updateFuelEntry(
        entry: LocalFuelEntry,
        outboxPayload: (LocalFuelEntry) -> String?,
    ) = databaseScope.updateFuelEntry(entry.toDatabaseFuelEntry()) { outboxPayload(it.toLocalFuelEntry()) }

    override suspend fun tombstoneFuelEntry(
        entry: LocalFuelEntry,
        outboxPayload: (LocalFuelEntry) -> String?,
    ) = databaseScope.tombstoneFuelEntry(entry.toDatabaseFuelEntry()) { outboxPayload(it.toLocalFuelEntry()) }
}
