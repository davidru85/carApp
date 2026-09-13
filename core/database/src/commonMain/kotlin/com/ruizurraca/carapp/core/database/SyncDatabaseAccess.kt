package com.ruizurraca.carapp.core.database

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull

data class SyncOutboxDatabaseRow(
    val sequence: Long,
    val entityType: String,
    val entityId: String,
    val payload: String,
    val localRevision: Long,
    val attemptCount: Long,
    val nextAttemptAt: Long,
    val lastErrorCode: String?,
)

data class SyncCursorDatabaseRow(
    val lastServerUpdatedAt: Long,
    val lastDocumentId: String,
)

data class SyncDatabaseCounts(
    val pending: Long,
    val retryable: Long,
    val poisoned: Long,
)

data class RemoteVehicleDatabaseWrite(
    val id: String,
    val ownerId: String,
    val name: String,
    val nameFold: String,
    val initialOdometerKm: Long,
    val brand: String?,
    val model: String?,
    val fuelType: String,
    val createdAt: Long,
    val updatedAt: Long,
    val serverUpdatedAt: Long,
    val deletedAt: Long?,
    val schemaVersion: Long,
)

data class RemoteFuelEntryDatabaseWrite(
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
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val serverUpdatedAt: Long,
    val deletedAt: Long?,
    val schemaVersion: Long,
)

data class QuarantineDatabaseWrite(
    val entityType: String,
    val entityId: String,
    val reason: String,
    val schemaVersion: Long,
    val serverUpdatedAt: Long,
    val rawJson: String,
    val createdAt: Long,
)

data class QuarantineDatabaseKey(
    val entityType: String,
    val entityId: String,
)

/** Database-owned sync mutations and their transaction boundaries (`D-38`, `E3-03`). */
class SyncDatabaseAccess(
    private val database: AppDatabase,
) {
    private val queries = database.databaseQueries
    private val mutations = DatabaseMutations(database)

    suspend fun isOwnerDatabaseEmpty(ownerId: String): Boolean =
        queries.countRowsOwnedBy(ownerId).awaitAsOne() == 0L && queries.countOutboxRows().awaitAsOne() == 0L

    suspend fun dueOutbox(
        now: Long,
        limit: Long,
    ): List<SyncOutboxDatabaseRow> =
        queries.selectDueOutbox(now, limit).awaitAsList().map { row ->
            SyncOutboxDatabaseRow(
                sequence = row.seq,
                entityType = row.entityType,
                entityId = row.entityId,
                payload = row.payload,
                localRevision = row.localRevision,
                attemptCount = row.attemptCount,
                nextAttemptAt = row.nextAttemptAt,
                lastErrorCode = row.lastErrorCode,
            )
        }

    suspend fun markSyncing(
        entityType: String,
        entityId: String,
    ) {
        when (entityType) {
            "VEHICLE" -> queries.markVehicleSyncing(entityId)
            "FUEL_ENTRY" -> queries.markFuelEntrySyncing(entityId)
            else -> error("Unknown sync entity type")
        }
    }

    suspend fun confirmPush(
        entityType: String,
        entityId: String,
        pushedLocalRevision: Long,
        serverUpdatedAt: Long?,
    ) {
        database.transaction {
            when (entityType) {
                "VEHICLE" -> {
                    queries.confirmVehiclePush(serverUpdatedAt, pushedLocalRevision, entityId)
                    queries.deleteConfirmedVehicleOutbox(entityId, pushedLocalRevision)
                }

                "FUEL_ENTRY" -> {
                    queries.confirmFuelEntryPush(serverUpdatedAt, pushedLocalRevision, entityId)
                    queries.deleteConfirmedFuelEntryOutbox(entityId, pushedLocalRevision)
                }

                else -> {
                    error("Unknown sync entity type")
                }
            }
        }
    }

    suspend fun failPush(
        entityType: String,
        entityId: String,
        attemptCount: Long,
        nextAttemptAt: Long,
        errorCode: String,
        poisoned: Boolean,
        cycleId: String,
    ) {
        database.transaction {
            queries.recordOutboxFailure(
                attemptCount = attemptCount,
                nextAttemptAt = nextAttemptAt,
                lastError = errorCode,
                lastErrorCode = errorCode,
                cycleId = cycleId,
                entityType = entityType,
                entityId = entityId,
            )
            val state = if (poisoned) "FAILED_POISONED" else "FAILED_RETRYABLE"
            when (entityType) {
                "VEHICLE" -> queries.markVehiclePushFailed(state, entityId)
                "FUEL_ENTRY" -> queries.markFuelEntryPushFailed(state, entityId)
                else -> error("Unknown sync entity type")
            }
        }
    }

    suspend fun cursor(entityType: String): SyncCursorDatabaseRow? =
        queries.selectSyncCursor(entityType).awaitAsOneOrNull()?.let { row ->
            SyncCursorDatabaseRow(row.lastServerUpdatedAt, row.lastDocumentId)
        }

    suspend fun applyPullPage(
        entityType: String,
        vehicles: List<RemoteVehicleDatabaseWrite>,
        fuelEntries: List<RemoteFuelEntryDatabaseWrite>,
        quarantines: List<QuarantineDatabaseWrite>,
        cursor: SyncCursorDatabaseRow,
    ): Set<QuarantineDatabaseKey> {
        val newQuarantines = mutableSetOf<QuarantineDatabaseKey>()
        database.transaction {
            vehicles.forEach { vehicle -> applyVehicleIfNewer(vehicle) }
            fuelEntries.forEach { entry -> applyFuelEntryIfNewer(entry) }
            quarantines.forEach { row ->
                if (queries.selectQuarantineByEntity(row.entityType, row.entityId).awaitAsOneOrNull() == null) {
                    newQuarantines += QuarantineDatabaseKey(row.entityType, row.entityId)
                }
                queries.upsertQuarantine(
                    row.entityType,
                    row.entityId,
                    row.reason,
                    row.schemaVersion,
                    row.serverUpdatedAt,
                    row.rawJson,
                    row.createdAt,
                )
            }
            queries.upsertSyncCursor(entityType, cursor.lastServerUpdatedAt, cursor.lastDocumentId)
        }
        return newQuarantines
    }

    suspend fun markConnectivityFailuresDue(now: Long) {
        queries.markConnectivityFailuresDue(now)
    }

    suspend fun resetFailed(now: Long) {
        database.transaction {
            queries.resetFailedOutbox(now)
            queries.resetFailedVehicles()
            queries.resetFailedFuelEntries()
        }
    }

    suspend fun counts(): SyncDatabaseCounts =
        SyncDatabaseCounts(
            pending = queries.countPendingSyncRows().awaitAsOne(),
            retryable = queries.countRetryableSyncRows().awaitAsOne(),
            poisoned = queries.countPoisonedSyncRows().awaitAsOne(),
        )

    suspend fun debugLines(): List<String> =
        buildList {
            queries.selectSyncDebugOutbox().awaitAsList().forEach { row ->
                add(
                    "outbox seq=${row.seq} type=${row.entityType} id=${row.entityId} " +
                        "attempt=${row.attemptCount} next=${row.nextAttemptAt} " +
                        "error=${row.lastErrorCode ?: "-"} cycle=${row.cycleId ?: "-"}",
                )
            }
            queries.selectSyncDebugCursors().awaitAsList().forEach { row ->
                add("cursor type=${row.entityType} at=${row.lastServerUpdatedAt} id=${row.lastDocumentId}")
            }
            queries.selectSyncDebugQuarantine().awaitAsList().forEach { row ->
                add(
                    "quarantine type=${row.entityType} id=${row.entityId} reason=${row.reason} " +
                        "schema=${row.schemaVersion} at=${row.serverUpdatedAt}",
                )
            }
            queries.selectSyncDebugVehicleStates().awaitAsList().forEach { row ->
                add("row type=VEHICLE id=${row.id} state=${row.syncState}")
            }
            queries.selectSyncDebugFuelEntryStates().awaitAsList().forEach { row ->
                add("row type=FUEL_ENTRY id=${row.id} state=${row.syncState}")
            }
        }

    private suspend fun applyVehicleIfNewer(vehicle: RemoteVehicleDatabaseWrite) {
        if (queries.selectOutboxByEntity("VEHICLE", vehicle.id).awaitAsOneOrNull() != null) return
        val local = queries.selectVehicleById(vehicle.id).awaitAsOneOrNull()
        if (local?.serverUpdatedAt != null && vehicle.serverUpdatedAt <= local.serverUpdatedAt) return
        mutations.applyRemoteVehicle(
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
            serverUpdatedAt = vehicle.serverUpdatedAt,
            deletedAt = vehicle.deletedAt,
            schemaVersion = vehicle.schemaVersion,
        )
        queries.recomputeVehicleCurrentOdometer(vehicle.id)
    }

    private suspend fun applyFuelEntryIfNewer(entry: RemoteFuelEntryDatabaseWrite) {
        if (queries.selectOutboxByEntity("FUEL_ENTRY", entry.id).awaitAsOneOrNull() != null) return
        val local = queries.selectFuelEntryById(entry.id).awaitAsOneOrNull()
        if (local?.serverUpdatedAt != null && entry.serverUpdatedAt <= local.serverUpdatedAt) return
        if (local == null) {
            mutations.insertFuelEntry(
                id = entry.id,
                ownerId = entry.ownerId,
                vehicleId = entry.vehicleId,
                date = entry.date,
                odometerKm = entry.odometerKm,
                litersScaled = entry.litersScaled,
                pricePerLiterScaled = entry.pricePerLiterScaled,
                totalCostMinor = entry.totalCostMinor,
                currency = entry.currency,
                isFullTank = entry.isFullTank.asLong(),
                hasMissedEntries = entry.hasMissedEntries.asLong(),
                notes = entry.notes,
                createdAt = entry.createdAt,
                updatedAt = entry.updatedAt,
                serverUpdatedAt = entry.serverUpdatedAt,
                deletedAt = entry.deletedAt,
                syncState = "SYNCED",
                localRevision = 0,
                localMutationSeq = 0,
                schemaVersion = entry.schemaVersion,
            )
        } else {
            mutations.updateFuelEntry(
                existingId = entry.id,
                id = entry.id,
                ownerId = entry.ownerId,
                vehicleId = entry.vehicleId,
                date = entry.date,
                odometerKm = entry.odometerKm,
                litersScaled = entry.litersScaled,
                pricePerLiterScaled = entry.pricePerLiterScaled,
                totalCostMinor = entry.totalCostMinor,
                currency = entry.currency,
                isFullTank = entry.isFullTank.asLong(),
                hasMissedEntries = entry.hasMissedEntries.asLong(),
                notes = entry.notes,
                createdAt = entry.createdAt,
                updatedAt = entry.updatedAt,
                serverUpdatedAt = entry.serverUpdatedAt,
                deletedAt = entry.deletedAt,
                syncState = "SYNCED",
                localRevision = local.localRevision,
                localMutationSeq = local.localMutationSeq,
                schemaVersion = entry.schemaVersion,
            )
        }
    }
}

private fun Boolean.asLong(): Long = if (this) 1L else 0L
