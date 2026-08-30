package com.ruizurraca.carapp.core.sync

import com.ruizurraca.carapp.core.common.SyncStatus
import com.ruizurraca.carapp.core.model.EntityId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Instant

class SyncContractModelsTest {
    private val entityId = EntityId("018f00d2-3ef4-7e02-8a01-4f78e21f9a10")
    private val serverUpdatedAt = Instant.fromEpochMilliseconds(1_000)

    @Test
    fun entitySnapshotPreservesTheSerializedLocalEntity() {
        val snapshot =
            EntitySnapshot(
                entityType = EntityType.VEHICLE,
                entityId = entityId,
                schemaVersion = 1,
                json = "{\"id\":\"vehicle-1\"}",
            )

        assertEquals(EntityType.VEHICLE, snapshot.entityType)
        assertEquals(entityId, snapshot.entityId)
        assertEquals(1, snapshot.schemaVersion)
        assertEquals("{\"id\":\"vehicle-1\"}", snapshot.json)
    }

    @Test
    fun remoteSnapshotPreservesRemoteMetadataAndPayload() {
        val snapshot =
            RemoteSnapshot(
                entityType = EntityType.FUEL_ENTRY,
                entityId = entityId,
                schemaVersion = 2,
                serverUpdatedAt = serverUpdatedAt,
                deleted = true,
                json = "{\"deleted\":true}",
            )

        assertEquals(EntityType.FUEL_ENTRY, snapshot.entityType)
        assertEquals(entityId, snapshot.entityId)
        assertEquals(2, snapshot.schemaVersion)
        assertEquals(serverUpdatedAt, snapshot.serverUpdatedAt)
        assertTrue(snapshot.deleted)
        assertEquals("{\"deleted\":true}", snapshot.json)
    }

    @Test
    fun remoteAckPreservesTheAcceptedEntityVersion() {
        val ack = RemoteAck(EntityType.VEHICLE, entityId, serverUpdatedAt)

        assertEquals(EntityType.VEHICLE, ack.entityType)
        assertEquals(entityId, ack.entityId)
        assertEquals(serverUpdatedAt, ack.serverUpdatedAt)
    }

    @Test
    fun initialCursorStartsAtTheEpochWithoutADocument() {
        assertEquals(Instant.fromEpochMilliseconds(0), RemoteCursor.INITIAL.lastServerUpdatedAt)
        assertNull(RemoteCursor.INITIAL.lastDocumentId)
    }

    @Test
    fun remotePagePreservesPaginationState() {
        val snapshot =
            RemoteSnapshot(
                EntityType.VEHICLE,
                entityId,
                1,
                serverUpdatedAt,
                false,
                "{}",
            )
        val cursor = RemoteCursor(serverUpdatedAt, entityId)
        val page = RemotePage(listOf(snapshot), cursor, false)

        assertEquals(listOf(snapshot), page.items)
        assertEquals(cursor, page.nextCursor)
        assertFalse(page.hasMore)
    }

    @Test
    fun cycleIdPreservesItsOpaqueValue() {
        assertEquals("cycle-1", CycleId("cycle-1").value)
    }

    @Test
    fun quarantineRecordPreservesTheRejectedRemotePayload() {
        val createdAt = Instant.fromEpochMilliseconds(2_000)
        val record =
            QuarantineRecord(
                entityType = EntityType.FUEL_ENTRY,
                entityId = entityId,
                reason = QuarantineReason.UnsupportedSchemaVersion,
                schemaVersion = 99,
                serverUpdatedAt = serverUpdatedAt,
                rawJson = "{\"schemaVersion\":99}",
                createdAt = createdAt,
            )

        assertEquals(EntityType.FUEL_ENTRY, record.entityType)
        assertEquals(entityId, record.entityId)
        assertEquals(QuarantineReason.UnsupportedSchemaVersion, record.reason)
        assertEquals(99, record.schemaVersion)
        assertEquals(serverUpdatedAt, record.serverUpdatedAt)
        assertEquals("{\"schemaVersion\":99}", record.rawJson)
        assertEquals(createdAt, record.createdAt)
        assertEquals(QuarantineReason.MalformedPayload, QuarantineReason.valueOf("MalformedPayload"))
    }

    @Test
    fun syncStatusesExposeTheirClosedStateData() {
        val pending = SyncStatus.Pending(3)
        val failed = SyncStatus.Failed(retryableCount = 2, poisonedCount = 1)

        assertSame(SyncStatus.Idle, SyncStatus.Idle)
        assertSame(SyncStatus.Syncing, SyncStatus.Syncing)
        assertEquals(3, pending.count)
        assertEquals(2, failed.retryableCount)
        assertEquals(1, failed.poisonedCount)
    }
}
