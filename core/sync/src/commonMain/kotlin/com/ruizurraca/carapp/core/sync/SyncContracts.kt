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

data class RemoteDocument(
    val entityType: EntityType,
    val documentId: EntityId,
    val serverUpdatedAt: Instant,
    val rawJson: String,
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
    val items: List<RemoteDocument>,
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

    /**
     * Requests a cycle and suspends until the cycle that serves this trigger completes, returning
     * its outcome. Symmetric with [retryFailed]. A refused cycle (offline or `LOCAL_OWNER`) is
     * `Ok(Unit)` with no error; a failed pull is `Err` carrying the failure. When a cycle is already
     * running, the caller joins the single pending follow-up rather than starting a second cycle, so
     * the serialization rules of `§9.1` are unchanged. A local write MUST use [requestSync], never
     * this method, so it never blocks on a network round trip.
     */
    suspend fun sync(reason: SyncTrigger): Outcome<Unit, AppError>

    suspend fun retryFailed(): Outcome<Unit, AppError>

    /** Redacted local diagnostics. Production controllers return an empty list. */
    suspend fun debugLines(): List<String> = emptyList()
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
