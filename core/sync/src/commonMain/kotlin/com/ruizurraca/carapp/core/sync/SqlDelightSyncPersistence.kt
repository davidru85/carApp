package com.ruizurraca.carapp.core.sync

import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.PersistenceError
import com.ruizurraca.carapp.core.database.QuarantineDatabaseWrite
import com.ruizurraca.carapp.core.database.RemoteFuelEntryDatabaseWrite
import com.ruizurraca.carapp.core.database.RemoteVehicleDatabaseWrite
import com.ruizurraca.carapp.core.database.SyncCursorDatabaseRow
import com.ruizurraca.carapp.core.database.SyncDatabaseAccess
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.OwnerId
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Instant

internal class SqlDelightSyncPersistence(
    private val access: SyncDatabaseAccess,
) : SyncPersistence {
    override suspend fun isOwnerDatabaseEmpty(ownerId: OwnerId): Boolean = access.isOwnerDatabaseEmpty(ownerId.value)

    override suspend fun dueOutbox(
        now: Instant,
        limit: Int,
    ): List<OutboxRecord> =
        access.dueOutbox(now.toEpochMilliseconds(), limit.toLong()).map { row ->
            OutboxRecord(
                sequence = row.sequence,
                entityType = EntityType.valueOf(row.entityType),
                entityId = EntityId(row.entityId),
                payload = row.payload,
                localRevision = row.localRevision,
                attemptCount = row.attemptCount.toInt(),
                nextAttemptAt = Instant.fromEpochMilliseconds(row.nextAttemptAt),
                lastErrorCode = row.lastErrorCode,
                deleted = row.payload.deletedOrFalse(),
            )
        }

    override suspend fun markSyncing(row: OutboxRecord) {
        access.markSyncing(row.entityType.name, row.entityId.value)
    }

    override suspend fun confirmPush(
        row: OutboxRecord,
        serverUpdatedAt: Instant?,
    ) {
        access.confirmPush(
            row.entityType.name,
            row.entityId.value,
            row.localRevision,
            serverUpdatedAt?.toEpochMilliseconds(),
        )
    }

    override suspend fun failPush(
        row: OutboxRecord,
        attemptCount: Int,
        nextAttemptAt: Instant,
        errorCode: String,
        poisoned: Boolean,
        cycleId: CycleId,
    ) {
        access.failPush(
            entityType = row.entityType.name,
            entityId = row.entityId.value,
            attemptCount = attemptCount.toLong(),
            nextAttemptAt = nextAttemptAt.toEpochMilliseconds(),
            errorCode = errorCode,
            poisoned = poisoned,
            cycleId = cycleId.value,
        )
    }

    override suspend fun cursor(entityType: EntityType): RemoteCursor =
        access.cursor(entityType.name)?.let { row ->
            RemoteCursor(Instant.fromEpochMilliseconds(row.lastServerUpdatedAt), EntityId(row.lastDocumentId))
        } ?: RemoteCursor.INITIAL

    override suspend fun applyPullPage(
        ownerId: OwnerId,
        entityType: EntityType,
        records: List<PullRecord>,
        cursor: RemoteCursor,
    ): List<QuarantineRecord> {
        val newQuarantines =
            access.applyPullPage(
                entityType = entityType.name,
                vehicles = records.filterIsInstance<PullRecord.Vehicle>().map(PullRecord.Vehicle::toDatabaseWrite),
                fuelEntries =
                    records.filterIsInstance<PullRecord.FuelEntry>().map(
                        PullRecord.FuelEntry::toDatabaseWrite,
                    ),
                quarantines =
                    records.filterIsInstance<PullRecord.Quarantined>().map {
                        it.record.toDatabaseWrite()
                    },
                cursor =
                    SyncCursorDatabaseRow(
                        cursor.lastServerUpdatedAt.toEpochMilliseconds(),
                        requireNotNull(cursor.lastDocumentId).value,
                    ),
            )
        return records
            .filterIsInstance<PullRecord.Quarantined>()
            .map { it.record }
            .filter { record ->
                newQuarantines.any { it.entityType == record.entityType.name && it.entityId == record.entityId.value }
            }
    }

    override suspend fun markConnectivityFailuresDue(now: Instant) {
        access.markConnectivityFailuresDue(now.toEpochMilliseconds())
    }

    override suspend fun resetFailed(now: Instant): Outcome<Unit, AppError> =
        try {
            access.resetFailed(now.toEpochMilliseconds())
            Outcome.Ok(Unit)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            Outcome.Err(PersistenceError.TransactionFailed)
        }

    override suspend fun counts(): SyncCounts =
        access.counts().let { counts ->
            SyncCounts(counts.pending.toInt(), counts.retryable.toInt(), counts.poisoned.toInt())
        }

    suspend fun debugLines(): List<String> = access.debugLines()
}

private fun PullRecord.Vehicle.toDatabaseWrite(): RemoteVehicleDatabaseWrite =
    RemoteVehicleDatabaseWrite(
        id = document.documentId.value,
        ownerId = ownerId.value,
        name = name,
        nameFold = nameFold,
        initialOdometerKm = initialOdometerKm,
        brand = brand,
        model = model,
        fuelType = fuelType,
        createdAt = createdAt.toEpochMilliseconds(),
        updatedAt = updatedAt.toEpochMilliseconds(),
        serverUpdatedAt = document.serverUpdatedAt.toEpochMilliseconds(),
        deletedAt = deletedAt?.toEpochMilliseconds(),
        schemaVersion = schemaVersion.toLong(),
    )

private fun PullRecord.FuelEntry.toDatabaseWrite(): RemoteFuelEntryDatabaseWrite =
    RemoteFuelEntryDatabaseWrite(
        id = document.documentId.value,
        ownerId = ownerId.value,
        vehicleId = vehicleId.value,
        date = date.toEpochMilliseconds(),
        odometerKm = odometerKm,
        litersScaled = litersScaled,
        pricePerLiterScaled = pricePerLiterScaled,
        totalCostMinor = totalCostMinor,
        currency = currency,
        isFullTank = isFullTank,
        hasMissedEntries = hasMissedEntries,
        notes = notes,
        createdAt = createdAt.toEpochMilliseconds(),
        updatedAt = updatedAt.toEpochMilliseconds(),
        serverUpdatedAt = document.serverUpdatedAt.toEpochMilliseconds(),
        deletedAt = deletedAt?.toEpochMilliseconds(),
        schemaVersion = schemaVersion.toLong(),
    )

private fun QuarantineRecord.toDatabaseWrite(): QuarantineDatabaseWrite =
    QuarantineDatabaseWrite(
        entityType.name,
        entityId.value,
        reason.name,
        schemaVersion.toLong(),
        serverUpdatedAt.toEpochMilliseconds(),
        rawJson,
        createdAt.toEpochMilliseconds(),
    )

private fun String.deletedOrFalse(): Boolean =
    runCatching {
        Json
            .parseToJsonElement(this)
            .jsonObject["deleted"]
            ?.jsonPrimitive
            ?.booleanOrNull == true
    }.getOrDefault(false)
