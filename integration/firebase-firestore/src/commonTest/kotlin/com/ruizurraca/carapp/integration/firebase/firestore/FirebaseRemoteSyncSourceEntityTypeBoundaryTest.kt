package com.ruizurraca.carapp.integration.firebase.firestore

import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.RemoteError
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.OwnerId
import com.ruizurraca.carapp.core.sync.EntitySnapshot
import com.ruizurraca.carapp.core.sync.EntityType
import com.ruizurraca.carapp.core.sync.RemoteAck
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.time.Instant

class FirebaseRemoteSyncSourceEntityTypeBoundaryTest {
    @Test
    fun vehiclePayloadWithEntityTypeSucceedsAndExcludesEntityTypeFromFields() =
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
                            json = vehicleJsonWithEntityType(entityId.value),
                        ),
                )

            assertIs<Outcome.Ok<RemoteAck>>(result)
            val write = gateway.writes.single()
            assertFalse("entityType" in write.fields)
        }

    @Test
    fun vehiclePayloadWithMismatchedEntityTypeReturnsInvalidArgument() =
        runTest {
            val gateway = RecordingFirestoreGateway()
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
                            json = vehicleJsonWithEntityType(entityId.value, entityTypeValue = "FUEL_ENTRY"),
                        ),
                )

            assertIs<Outcome.Err<RemoteError>>(result)
            assertEquals(RemoteError.InvalidArgument, result.error)
        }

    @Test
    fun vehiclePayloadWithoutEntityTypeReturnsInvalidArgument() =
        runTest {
            val gateway = RecordingFirestoreGateway()
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
                            json = vehicleJsonWithoutEntityType(entityId.value),
                        ),
                )

            assertIs<Outcome.Err<RemoteError>>(result)
            assertEquals(RemoteError.InvalidArgument, result.error)
        }

    @Test
    fun fuelEntryPayloadWithEntityTypeSucceedsAndExcludesEntityTypeFromFields() =
        runTest {
            val serverUpdatedAt = Instant.fromEpochMilliseconds(1_767_225_600_000L)
            val gateway = RecordingFirestoreGateway(serverUpdatedAt)
            val source = FirebaseRemoteSyncSource(gateway)
            val entityId = EntityId("123e4567-e89b-42d3-a456-426614174001")

            val result =
                source.pushSnapshot(
                    ownerId = OwnerId("anonymous-owner"),
                    snapshot =
                        EntitySnapshot(
                            entityType = EntityType.FUEL_ENTRY,
                            entityId = entityId,
                            schemaVersion = 1,
                            json = fuelEntryJsonWithEntityType(entityId.value),
                        ),
                )

            assertIs<Outcome.Ok<RemoteAck>>(result)
            val write = gateway.writes.single()
            assertFalse("entityType" in write.fields)
        }

    @Test
    fun fuelEntryPayloadWithMismatchedEntityTypeReturnsInvalidArgument() =
        runTest {
            val gateway = RecordingFirestoreGateway()
            val source = FirebaseRemoteSyncSource(gateway)
            val entityId = EntityId("123e4567-e89b-42d3-a456-426614174001")

            val result =
                source.pushSnapshot(
                    ownerId = OwnerId("anonymous-owner"),
                    snapshot =
                        EntitySnapshot(
                            entityType = EntityType.FUEL_ENTRY,
                            entityId = entityId,
                            schemaVersion = 1,
                            json = fuelEntryJsonWithEntityType(entityId.value, entityTypeValue = "VEHICLE"),
                        ),
                )

            assertIs<Outcome.Err<RemoteError>>(result)
            assertEquals(RemoteError.InvalidArgument, result.error)
        }
}

private fun vehicleJsonWithEntityType(
    id: String,
    entityTypeValue: String = "VEHICLE",
): String =
    """
    {
      "entityType":"$entityTypeValue",
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

private fun vehicleJsonWithoutEntityType(id: String): String =
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

private fun fuelEntryJsonWithEntityType(
    id: String,
    entityTypeValue: String = "FUEL_ENTRY",
): String =
    """
    {
      "entityType":"$entityTypeValue",
      "id":"$id",
      "ownerId":"anonymous-owner",
      "vehicleId":"123e4567-e89b-42d3-a456-426614174000",
      "date":1700000000000,
      "odometerKm":100,
      "litersScaled":50000,
      "pricePerLiterScaled":150000,
      "totalCostMinor":7500,
      "currency":"EUR",
      "isFullTank":true,
      "hasMissedEntries":false,
      "odometerInconsistent":false,
      "notes":null,
      "createdAt":1700000000000,
      "updatedAt":1700000000000,
      "deleted":false,
      "deletedAt":null,
      "schemaVersion":1
    }
    """.trimIndent()
