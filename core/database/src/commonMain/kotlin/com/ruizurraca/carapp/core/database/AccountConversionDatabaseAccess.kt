package com.ruizurraca.carapp.core.database

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull

enum class AccountConversionPhase {
    SNAPSHOT_CAPTURED,
    SESSION_SWITCHED,
    REMOTE_REPLACED,
    LOCAL_REPLACED,
}

data class AccountConversionSnapshotRow(
    val entityType: String,
    val entityId: String,
    val payload: String,
    val localRevision: Long,
    val localMutationSeq: Long,
    val remoteServerUpdatedAt: Long?,
)

data class AccountConversionOperation(
    val anonymousUid: String,
    val permanentUid: String?,
    val cleanupTicket: String?,
    val phase: AccountConversionPhase,
    val snapshots: List<AccountConversionSnapshotRow>,
)

interface AccountConversionStore {
    suspend fun load(): AccountConversionOperation?

    suspend fun captureIfAbsent(
        anonymousUid: String,
        vehiclePayload: (VehicleDatabaseRow) -> String,
        fuelEntryPayload: (FuelEntryDatabaseRow) -> String,
    ): AccountConversionOperation

    suspend fun saveCleanupTicket(ticket: String)

    suspend fun savePermanentUid(permanentUid: String)

    suspend fun saveRemoteAck(
        entityType: String,
        entityId: String,
        serverUpdatedAt: Long,
    )

    suspend fun markRemoteReplaced()

    suspend fun replaceLocalSnapshot(
        permanentUid: String,
        vehicles: List<VehicleDatabaseRow>,
        fuelEntries: List<FuelEntryDatabaseRow>,
    )

    suspend fun clear()
}

/** SQLDelight-owned durable operation store for the D-61 replacement flow. */
class AccountConversionDatabaseAccess(
    private val database: AppDatabase,
) : AccountConversionStore {
    private val queries = database.databaseQueries

    override suspend fun load(): AccountConversionOperation? =
        queries.selectAccountConversionOperation().awaitAsOneOrNull()?.let { operation ->
            AccountConversionOperation(
                anonymousUid = operation.anonymousUid,
                permanentUid = operation.permanentUid,
                cleanupTicket = operation.cleanupTicket,
                phase = AccountConversionPhase.valueOf(operation.phase),
                snapshots =
                    queries.selectAccountConversionSnapshots().awaitAsList().map { row ->
                        AccountConversionSnapshotRow(
                            entityType = row.entityType,
                            entityId = row.entityId,
                            payload = row.payload,
                            localRevision = row.localRevision,
                            localMutationSeq = row.localMutationSeq,
                            remoteServerUpdatedAt = row.remoteServerUpdatedAt,
                        )
                    },
            )
        }

    override suspend fun captureIfAbsent(
        anonymousUid: String,
        vehiclePayload: (VehicleDatabaseRow) -> String,
        fuelEntryPayload: (FuelEntryDatabaseRow) -> String,
    ): AccountConversionOperation {
        load()?.let { return it }
        database.transaction {
            queries.insertAccountConversionOperation(anonymousUid)
            queries.selectVehiclesForAdoption(anonymousUid).awaitAsList().forEach { row ->
                val vehicle = row.toVehicleDatabaseRow()
                queries.insertAccountConversionSnapshot(
                    entityType = "VEHICLE",
                    entityId = vehicle.id,
                    payload = vehiclePayload(vehicle),
                    localRevision = vehicle.localRevision,
                    localMutationSeq = vehicle.localMutationSeq,
                )
            }
            queries.selectFuelEntriesForAdoption(anonymousUid).awaitAsList().forEach { row ->
                val fuelEntry = row.toFuelEntryDatabaseRow()
                queries.insertAccountConversionSnapshot(
                    entityType = "FUEL_ENTRY",
                    entityId = fuelEntry.id,
                    payload = fuelEntryPayload(fuelEntry),
                    localRevision = fuelEntry.localRevision,
                    localMutationSeq = fuelEntry.localMutationSeq,
                )
            }
        }
        return checkNotNull(load())
    }

    override suspend fun saveCleanupTicket(ticket: String) {
        queries.saveAccountConversionCleanupTicket(ticket)
    }

    override suspend fun savePermanentUid(permanentUid: String) {
        queries.saveAccountConversionPermanentUid(permanentUid)
    }

    override suspend fun saveRemoteAck(
        entityType: String,
        entityId: String,
        serverUpdatedAt: Long,
    ) {
        queries.saveAccountConversionRemoteAck(serverUpdatedAt, entityType, entityId)
    }

    override suspend fun markRemoteReplaced() {
        queries.setAccountConversionPhase(AccountConversionPhase.REMOTE_REPLACED.name)
    }

    override suspend fun replaceLocalSnapshot(
        permanentUid: String,
        vehicles: List<VehicleDatabaseRow>,
        fuelEntries: List<FuelEntryDatabaseRow>,
    ) {
        require(vehicles.all { it.ownerId == permanentUid })
        require(fuelEntries.all { it.ownerId == permanentUid })
        database.transaction {
            queries.deleteAllFuelEntriesForAccountConversion()
            queries.deleteAllVehiclesForAccountConversion()
            queries.deleteAllOutboxForAccountConversion()
            queries.deleteAllSyncCursorsForAccountConversion()
            queries.deleteAllQuarantineForAccountConversion()
            vehicles.forEach { vehicle -> queries.insertVehicleRow(vehicle) }
            fuelEntries.forEach { fuelEntry -> queries.insertFuelEntryRow(fuelEntry) }
            vehicles.forEach { vehicle -> queries.recomputeVehicleCurrentOdometer(vehicle.id) }
            queries.setAccountConversionPhase(AccountConversionPhase.LOCAL_REPLACED.name)
        }
    }

    override suspend fun clear() {
        database.transaction {
            queries.deleteAccountConversionSnapshots()
            queries.deleteAccountConversionOperation()
        }
    }
}

private suspend fun DatabaseQueries.insertVehicleRow(vehicle: VehicleDatabaseRow) {
    insertVehicleRow(
        id = vehicle.id,
        ownerId = vehicle.ownerId,
        name = vehicle.name,
        nameFold = vehicle.nameFold,
        initialOdometerKm = vehicle.initialOdometerKm,
        currentOdometerKm = vehicle.currentOdometerKm,
        brand = vehicle.brand,
        model = vehicle.model,
        fuelType = vehicle.fuelType,
        createdAt = vehicle.createdAt,
        updatedAt = vehicle.updatedAt,
        serverUpdatedAt = vehicle.serverUpdatedAt,
        deleted = if (vehicle.deletedAt == null) 0 else 1,
        deletedAt = vehicle.deletedAt,
        syncState = vehicle.syncState,
        localRevision = vehicle.localRevision,
        localMutationSeq = vehicle.localMutationSeq,
        schemaVersion = vehicle.schemaVersion,
    )
}

private suspend fun DatabaseQueries.insertFuelEntryRow(fuelEntry: FuelEntryDatabaseRow) {
    insertFuelEntryRow(
        id = fuelEntry.id,
        ownerId = fuelEntry.ownerId,
        vehicleId = fuelEntry.vehicleId,
        date = fuelEntry.date,
        odometerKm = fuelEntry.odometerKm,
        litersScaled = fuelEntry.litersScaled,
        pricePerLiterScaled = fuelEntry.pricePerLiterScaled,
        totalCostMinor = fuelEntry.totalCostMinor,
        currency = fuelEntry.currency,
        isFullTank = if (fuelEntry.isFullTank) 1 else 0,
        hasMissedEntries = if (fuelEntry.hasMissedEntries) 1 else 0,
        odometerInconsistent = if (fuelEntry.odometerInconsistent) 1 else 0,
        notes = fuelEntry.notes,
        createdAt = fuelEntry.createdAt,
        updatedAt = fuelEntry.updatedAt,
        serverUpdatedAt = fuelEntry.serverUpdatedAt,
        deleted = if (fuelEntry.deletedAt == null) 0 else 1,
        deletedAt = fuelEntry.deletedAt,
        syncState = fuelEntry.syncState,
        localRevision = fuelEntry.localRevision,
        localMutationSeq = fuelEntry.localMutationSeq,
        schemaVersion = fuelEntry.schemaVersion,
    )
}
