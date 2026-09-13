package com.ruizurraca.carapp.core.sync

import com.ruizurraca.carapp.core.common.AppClock
import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.ConnectivityObserver
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.OwnerContext
import com.ruizurraca.carapp.core.common.SyncStatus
import com.ruizurraca.carapp.core.common.SyncTrigger
import com.ruizurraca.carapp.core.common.UuidGenerator
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.OwnerId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Instant

fun interface JitterSource {
    fun nextInt(from: Int, until: Int): Int
}

internal data class OutboxRecord(
    val sequence: Long,
    val entityType: EntityType,
    val entityId: EntityId,
    val payload: String,
    val localRevision: Long,
    val attemptCount: Int,
    val nextAttemptAt: Instant,
    val lastErrorCode: String?,
    val deleted: Boolean,
)

internal data class SyncCounts(
    val pending: Int,
    val retryable: Int,
    val poisoned: Int,
)

internal sealed interface PullRecord {
    data class Vehicle(
        val document: RemoteDocument,
        val ownerId: OwnerId,
        val name: String,
        val nameFold: String,
        val initialOdometerKm: Long,
        val brand: String?,
        val model: String?,
        val fuelType: String,
        val createdAt: Instant,
        val updatedAt: Instant,
        val deletedAt: Instant?,
        val schemaVersion: Int,
    ) : PullRecord

    data class FuelEntry(
        val document: RemoteDocument,
        val ownerId: OwnerId,
        val vehicleId: EntityId,
        val date: Instant,
        val odometerKm: Long,
        val litersScaled: Long,
        val pricePerLiterScaled: Long,
        val totalCostMinor: Long,
        val currency: String,
        val isFullTank: Boolean,
        val hasMissedEntries: Boolean,
        val notes: String?,
        val createdAt: Instant,
        val updatedAt: Instant,
        val deletedAt: Instant?,
        val schemaVersion: Int,
    ) : PullRecord

    data class Quarantined(
        val record: QuarantineRecord,
    ) : PullRecord
}

internal interface SyncPersistence {
    suspend fun isOwnerDatabaseEmpty(ownerId: OwnerId): Boolean

    suspend fun dueOutbox(now: Instant, limit: Int): List<OutboxRecord>

    suspend fun markSyncing(row: OutboxRecord)

    suspend fun confirmPush(row: OutboxRecord, serverUpdatedAt: Instant?)

    suspend fun failPush(
        row: OutboxRecord,
        attemptCount: Int,
        nextAttemptAt: Instant,
        errorCode: String,
        poisoned: Boolean,
        cycleId: CycleId,
    )

    suspend fun cursor(entityType: EntityType): RemoteCursor

    suspend fun applyPullPage(
        ownerId: OwnerId,
        entityType: EntityType,
        records: List<PullRecord>,
        cursor: RemoteCursor,
    )

    suspend fun markConnectivityFailuresDue(now: Instant)

    suspend fun resetFailed(now: Instant): Outcome<Unit, AppError>

    suspend fun counts(): SyncCounts
}

internal class DefaultSyncController(
    private val scope: CoroutineScope,
    private val ownerContext: OwnerContext,
    private val connectivity: ConnectivityObserver,
    private val remote: RemoteSyncSource,
    private val persistence: SyncPersistence,
    private val clock: AppClock,
    private val uuidGenerator: UuidGenerator,
    private val jitter: JitterSource,
    private val adoption: suspend () -> Outcome<Unit, AppError> = { Outcome.Ok(Unit) },
) : SyncController {
    private val mutableStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    override val status: StateFlow<SyncStatus> = mutableStatus

    override fun requestSync(reason: SyncTrigger) = Unit

    override suspend fun retryFailed(): Outcome<Unit, AppError> = Outcome.Ok(Unit)
}

internal fun retryDelayMillis(
    attemptCount: Int,
    jitter: JitterSource,
): Long = 0L
