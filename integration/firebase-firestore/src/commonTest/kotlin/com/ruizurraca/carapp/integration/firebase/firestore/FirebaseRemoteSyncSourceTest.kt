package com.ruizurraca.carapp.integration.firebase.firestore

import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.OwnerId
import com.ruizurraca.carapp.core.sync.EntitySnapshot
import com.ruizurraca.carapp.core.sync.EntityType
import com.ruizurraca.carapp.core.sync.RemoteAck
import com.ruizurraca.carapp.core.sync.RemoteCursor
import com.ruizurraca.carapp.core.sync.RemotePage
import kotlinx.serialization.json.Json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant

class FirebaseRemoteSyncSourceTest {
    @Test
    fun constructionDisablesPersistentFirestoreCaching() {
        val gateway = RecordingFirestoreGateway()

        FirebaseRemoteSyncSource(gateway)

        assertEquals(1, gateway.memoryOnlyConfigurationCount)
    }

    @Test
    fun vehiclePushUsesTheOwnerPathAndReturnsTheServerTimestamp() =
        runTest {
            val serverUpdatedAt = Instant.fromEpochMilliseconds(1_767_225_600_000L)
            val gateway = RecordingFirestoreGateway(serverUpdatedAt)
            val source = FirebaseRemoteSyncSource(gateway)
            val entityId = EntityId("123e4567-e89b-42d3-a456-426614174000")

            val result =
                source.pushSnapshot(
                    ownerId = OwnerId("anonymous-owner"),
                    snapshot =
                        EntitySnapshot(
                            entityType = EntityType.VEHICLE,
                            entityId = entityId,
                            schemaVersion = 1,
                            json = vehicleJson(entityId.value),
                        ),
                )

            assertEquals(
                RemoteAck(EntityType.VEHICLE, entityId, serverUpdatedAt),
                assertIs<Outcome.Ok<RemoteAck>>(result).value,
            )
            val write = gateway.writes.single()
            assertEquals("users/anonymous-owner/vehicles/${entityId.value}", write.path)
            assertEquals(FirestoreTimestamp(1_700_000_000_000L), write.fields.getValue("createdAt"))
            assertEquals(FirestoreServerTimestamp, write.fields.getValue("updatedAt"))
            assertEquals(FirestoreNull, write.fields.getValue("deletedAt"))
        }

    @Test
    fun vehiclePullReturnsOrderedRemoteSnapshotsWithoutProviderTypes() =
        runTest {
            val entityId = EntityId("123e4567-e89b-42d3-a456-426614174000")
            val serverUpdatedAt = Instant.fromEpochMilliseconds(1_767_225_600_000L)
            val gateway =
                RecordingFirestoreGateway(
                    documents = listOf(vehicleDocument(entityId.value, serverUpdatedAt)),
                )
            val source = FirebaseRemoteSyncSource(gateway)

            val result =
                source.pullChanges(
                    ownerId = OwnerId("anonymous-owner"),
                    entityType = EntityType.VEHICLE,
                    cursor = RemoteCursor.INITIAL,
                    limit = 50,
                )

            val page = assertIs<Outcome.Ok<RemotePage>>(result).value
            val item = page.items.single()
            assertEquals(EntityType.VEHICLE, item.entityType)
            assertEquals(entityId, item.entityId)
            assertEquals(1, item.schemaVersion)
            assertEquals(serverUpdatedAt, item.serverUpdatedAt)
            assertEquals(false, item.deleted)
            assertEquals(Json.parseToJsonElement(vehicleJson(entityId.value)), Json.parseToJsonElement(item.json))
            assertEquals(RemoteCursor(serverUpdatedAt, entityId), page.nextCursor)
            assertEquals(false, page.hasMore)
            assertEquals(
                FirestoreQuery(
                    path = "users/anonymous-owner/vehicles",
                    entityType = EntityType.VEHICLE,
                    updatedAtOrAfter = Instant.fromEpochMilliseconds(0),
                    afterDocumentId = null,
                    limit = 50,
                ),
                gateway.queries.single(),
            )
        }
}

private class RecordingFirestoreGateway(
    private val serverUpdatedAt: Instant = Instant.fromEpochMilliseconds(0),
    private val documents: List<FirestoreDocument> = emptyList(),
) : FirestoreGateway {
    var memoryOnlyConfigurationCount = 0
    val writes = mutableListOf<FirestoreWrite>()
    val queries = mutableListOf<FirestoreQuery>()

    override fun configureMemoryOnlyCache() {
        memoryOnlyConfigurationCount += 1
    }

    override suspend fun writeDocument(write: FirestoreWrite): Instant {
        writes += write
        return serverUpdatedAt
    }

    override suspend fun queryDocuments(query: FirestoreQuery): List<FirestoreDocument> {
        queries += query
        return documents
    }
}

private fun vehicleDocument(
    id: String,
    serverUpdatedAt: Instant,
): FirestoreDocument =
    FirestoreDocument(
        id = id,
        fields =
            mapOf(
                "id" to FirestoreString(id),
                "ownerId" to FirestoreString("anonymous-owner"),
                "name" to FirestoreString("Roadster"),
                "initialOdometerKm" to FirestoreLong(0),
                "brand" to FirestoreNull,
                "model" to FirestoreNull,
                "fuelType" to FirestoreString("GASOLINE"),
                "createdAt" to FirestoreTimestamp(1_700_000_000_000L),
                "updatedAt" to FirestoreTimestamp(serverUpdatedAt.toEpochMilliseconds()),
                "deleted" to FirestoreBoolean(false),
                "deletedAt" to FirestoreNull,
                "schemaVersion" to FirestoreLong(1),
            ),
    )

private fun vehicleJson(id: String): String =
    """
    {
      "id":"$id",
      "ownerId":"anonymous-owner",
      "name":"Roadster",
      "initialOdometerKm":0,
      "brand":null,
      "model":null,
      "fuelType":"GASOLINE",
      "createdAt":1700000000000,
      "updatedAt":1700000000000,
      "deleted":false,
      "deletedAt":null,
      "schemaVersion":1
    }
    """.trimIndent()
