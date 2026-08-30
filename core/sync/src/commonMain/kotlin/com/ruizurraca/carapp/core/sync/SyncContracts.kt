package com.ruizurraca.carapp.core.sync

import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.RemoteError
import com.ruizurraca.carapp.core.common.SyncStatus
import com.ruizurraca.carapp.core.common.SyncTrigger
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.OwnerId
import kotlinx.coroutines.flow.StateFlow
import kotlin.jvm.JvmInline
import kotlin.time.Instant

enum class EntityType(
    val collection: String,
) {
    VEHICLE("vehicles"),
    FUEL_ENTRY("fuelEntries"),
}

data class EntitySnapshot(
    val entityType: EntityType,
    val entityId: EntityId,
    val schemaVersion: Int,
    val json: String,
)

data class RemoteSnapshot(
    val entityType: EntityType,
    val entityId: EntityId,
    val schemaVersion: Int,
    val serverUpdatedAt: Instant,
    val deleted: Boolean,
    val json: String,
)

data class RemoteAck(
    val entityType: EntityType,
    val entityId: EntityId,
    val serverUpdatedAt: Instant,
)

data class RemoteCursor(
    val lastServerUpdatedAt: Instant,
    val lastDocumentId: EntityId?,
) {
    companion object {
        val INITIAL = RemoteCursor(Instant.fromEpochMilliseconds(0), null)
    }
}

data class RemotePage(
    val items: List<RemoteSnapshot>,
    val nextCursor: RemoteCursor,
    val hasMore: Boolean,
)

@JvmInline
value class CycleId(
    val value: String,
)

enum class QuarantineReason {
    UnsupportedSchemaVersion,
    MalformedPayload,
}

data class QuarantineRecord(
    val entityType: EntityType,
    val entityId: EntityId,
    val reason: QuarantineReason,
    val schemaVersion: Int,
    val serverUpdatedAt: Instant,
    val rawJson: String,
    val createdAt: Instant,
)

interface SyncController {
    val status: StateFlow<SyncStatus>

    fun requestSync(reason: SyncTrigger)

    suspend fun retryFailed(): Outcome<Unit, AppError>
}

interface RemoteSyncSource {
    suspend fun pushSnapshot(
        ownerId: OwnerId,
        snapshot: EntitySnapshot,
    ): Outcome<RemoteAck, RemoteError>

    suspend fun pullChanges(
        ownerId: OwnerId,
        entityType: EntityType,
        cursor: RemoteCursor,
        limit: Int,
    ): Outcome<RemotePage, RemoteError>
}
