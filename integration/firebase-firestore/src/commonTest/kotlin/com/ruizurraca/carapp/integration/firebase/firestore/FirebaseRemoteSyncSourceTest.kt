package com.ruizurraca.carapp.integration.firebase.firestore

import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.OwnerId
import com.ruizurraca.carapp.core.sync.EntitySnapshot
import com.ruizurraca.carapp.core.sync.EntityType
import com.ruizurraca.carapp.core.sync.RemoteAck
import kotlinx.coroutines.test.runTest
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

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
}

private class RecordingFirestoreGateway : FirestoreGateway {
    constructor(serverUpdatedAt: Instant = Instant.fromEpochMilliseconds(0)) {
        this.serverUpdatedAt = serverUpdatedAt
    }

    var memoryOnlyConfigurationCount = 0
    lateinit var serverUpdatedAt: Instant
    val writes = mutableListOf<FirestoreWrite>()

    override fun configureMemoryOnlyCache() {
        memoryOnlyConfigurationCount += 1
    }

    override suspend fun writeDocument(write: FirestoreWrite): Instant {
        writes += write
        return serverUpdatedAt
    }
}

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
