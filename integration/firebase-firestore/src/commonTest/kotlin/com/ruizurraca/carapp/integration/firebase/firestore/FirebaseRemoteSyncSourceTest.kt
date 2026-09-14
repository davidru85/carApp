package com.ruizurraca.carapp.integration.firebase.firestore

import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.RemoteError
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.OwnerId
import com.ruizurraca.carapp.core.sync.EntitySnapshot
import com.ruizurraca.carapp.core.sync.EntityType
import com.ruizurraca.carapp.core.sync.RemoteAck
import com.ruizurraca.carapp.core.sync.RemoteCursor
import com.ruizurraca.carapp.core.sync.RemotePage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
    fun vehiclePullReturnsOrderedRawDocumentsWithoutProviderTypes() =
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
            assertEquals(entityId, item.documentId)
            assertEquals(serverUpdatedAt, item.serverUpdatedAt)
            assertEquals(
                Json.parseToJsonElement(vehicleRemoteJson(entityId.value, serverUpdatedAt.toEpochMilliseconds())),
                Json.parseToJsonElement(item.rawJson),
            )
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

    @Test
    fun fuelEntryPullReturnsTheCompleteRawRemoteDocument() =
        runTest {
            val entityId = EntityId("123e4567-e89b-42d3-a456-426614174001")
            val serverUpdatedAt = Instant.fromEpochMilliseconds(1_767_225_600_000L)
            val gateway =
                RecordingFirestoreGateway(
                    documents = listOf(fuelEntryDocument(entityId.value, serverUpdatedAt)),
                )
            val source = FirebaseRemoteSyncSource(gateway)

            val result =
                source.pullChanges(
                    ownerId = OwnerId("anonymous-owner"),
                    entityType = EntityType.FUEL_ENTRY,
                    cursor = RemoteCursor.INITIAL,
                    limit = 50,
                )

            val item = assertIs<Outcome.Ok<RemotePage>>(result).value.items.single()
            assertEquals(EntityType.FUEL_ENTRY, item.entityType)
            assertEquals(entityId, item.documentId)
            assertEquals(serverUpdatedAt, item.serverUpdatedAt)
            assertEquals(
                Json.parseToJsonElement(
                    fuelEntryRemoteJson(entityId.value, serverUpdatedAt.toEpochMilliseconds()),
                ),
                Json.parseToJsonElement(item.rawJson),
            )
            assertEquals("users/anonymous-owner/fuelEntries", gateway.queries.single().path)
        }

    @Test
    fun malformedProductFieldsRemainSuccessfulRawPullItems() =
        runTest {
            val serverUpdatedAt = Instant.fromEpochMilliseconds(1_767_225_600_000L)
            // One product field is missing, another has the wrong primitive type. Neither may fail
            // the page: the raw values reach `:core:sync` for `§9.5` quarantine (`D-170`).
            val malformed =
                FirestoreDocument(
                    id = "vehicle-malformed",
                    fields =
                        mapOf(
                            "name" to true,
                            "updatedAt" to serverUpdatedAt.toEpochMilliseconds(),
                        ),
                )

            val result =
                FirebaseRemoteSyncSource(RecordingFirestoreGateway(documents = listOf(malformed))).pullChanges(
                    ownerId = OwnerId("anonymous-owner"),
                    entityType = EntityType.VEHICLE,
                    cursor = RemoteCursor.INITIAL,
                    limit = 200,
                )

            val item = assertIs<Outcome.Ok<RemotePage>>(result).value.items.single()
            assertEquals("vehicle-malformed", item.documentId.value)
            assertEquals(serverUpdatedAt, item.serverUpdatedAt)
            val json = Json.parseToJsonElement(item.rawJson).jsonObject
            assertEquals(true, json["name"]?.jsonPrimitive?.boolean)
        }

    @Test
    fun missingProductFieldReachesTheEngineAsARawDocumentForQuarantine() =
        runTest {
            val serverUpdatedAt = Instant.fromEpochMilliseconds(1_767_225_600_000L)
            // `initialOdometerKm` is absent and `name` is a boolean. Before the `D-170` transport fix
            // this threw inside the per-field typed read and never reached `:core:sync`.
            val missingField =
                FirestoreDocument(
                    id = "vehicle-missing",
                    fields =
                        mapOf(
                            "id" to "vehicle-missing",
                            "ownerId" to "anonymous-owner",
                            "name" to true,
                            "brand" to null,
                            "model" to null,
                            "fuelType" to "GASOLINE",
                            "createdAt" to 1_700_000_000_000L,
                            "updatedAt" to serverUpdatedAt.toEpochMilliseconds(),
                            "deleted" to false,
                            "deletedAt" to null,
                            "schemaVersion" to 1L,
                        ),
                )

            val page =
                assertIs<Outcome.Ok<RemotePage>>(
                    FirebaseRemoteSyncSource(RecordingFirestoreGateway(documents = listOf(missingField))).pullChanges(
                        ownerId = OwnerId("anonymous-owner"),
                        entityType = EntityType.VEHICLE,
                        cursor = RemoteCursor.INITIAL,
                        limit = 200,
                    ),
                ).value

            val item = page.items.single()
            assertEquals(serverUpdatedAt, item.serverUpdatedAt)
            // The raw field map is preserved so `:core:sync` can classify it, and `name` keeps its
            // untyped raw value rather than being coerced or dropped.
            assertEquals(
                "vehicle-missing",
                Json
                    .parseToJsonElement(item.rawJson)
                    .jsonObject["id"]
                    ?.jsonPrimitive
                    ?.content,
            )
            assertEquals(
                true,
                Json
                    .parseToJsonElement(item.rawJson)
                    .jsonObject["name"]
                    ?.jsonPrimitive
                    ?.boolean,
            )
        }

    @Test
    fun missingOrderingTimestampFailsThePageClosed() =
        runTest {
            // `updatedAt` is the ordering timestamp `RemoteDocument` genuinely needs. A document
            // without it must fail the page as a closed `Outcome`, never throw a
            // `NoSuchElementException` out of `pullChanges` (`§6`, `§9.5`).
            val withoutUpdatedAt =
                FirestoreDocument(
                    id = "vehicle-no-timestamp",
                    fields =
                        mapOf(
                            "id" to "vehicle-no-timestamp",
                            "ownerId" to "anonymous-owner",
                            "name" to "Roadster",
                        ),
                )

            val result =
                FirebaseRemoteSyncSource(RecordingFirestoreGateway(documents = listOf(withoutUpdatedAt))).pullChanges(
                    ownerId = OwnerId("anonymous-owner"),
                    entityType = EntityType.VEHICLE,
                    cursor = RemoteCursor.INITIAL,
                    limit = 200,
                )

            assertEquals(RemoteError.InvalidArgument, assertIs<Outcome.Err<RemoteError>>(result).error)
        }

    @Test
    fun wrongTypedOrderingTimestampFailsThePageClosed() =
        runTest {
            val mistyped =
                FirestoreDocument(
                    id = "vehicle-bad-timestamp",
                    fields =
                        mapOf(
                            "id" to "vehicle-bad-timestamp",
                            "ownerId" to "anonymous-owner",
                            "name" to "Roadster",
                            "updatedAt" to "not-a-timestamp",
                        ),
                )

            val result =
                FirebaseRemoteSyncSource(RecordingFirestoreGateway(documents = listOf(mistyped))).pullChanges(
                    ownerId = OwnerId("anonymous-owner"),
                    entityType = EntityType.VEHICLE,
                    cursor = RemoteCursor.INITIAL,
                    limit = 200,
                )

            assertEquals(RemoteError.InvalidArgument, assertIs<Outcome.Err<RemoteError>>(result).error)
        }

    @Test
    fun unauthenticatedPushRefreshesTheTokenAndRetriesOnce() =
        runTest {
            val serverUpdatedAt = Instant.fromEpochMilliseconds(1_767_225_600_000L)
            val gateway =
                RecordingFirestoreGateway(
                    serverUpdatedAt = serverUpdatedAt,
                    writeFailures = mutableListOf(FirestoreGatewayFailure.UNAUTHENTICATED),
                )
            val source = FirebaseRemoteSyncSource(gateway)
            val snapshot = vehicleSnapshot()

            val result = source.pushSnapshot(OwnerId("anonymous-owner"), snapshot)

            assertEquals(
                RemoteAck(snapshot.entityType, snapshot.entityId, serverUpdatedAt),
                assertIs<Outcome.Ok<RemoteAck>>(result).value,
            )
            assertEquals(1, gateway.tokenRefreshCount)
            assertEquals(2, gateway.writes.size)
        }

    @Test
    fun secondUnauthenticatedPushReturnsUnauthenticatedWithoutAnotherRetry() =
        runTest {
            val gateway =
                RecordingFirestoreGateway(
                    writeFailures =
                        mutableListOf(
                            FirestoreGatewayFailure.UNAUTHENTICATED,
                            FirestoreGatewayFailure.UNAUTHENTICATED,
                        ),
                )
            val source = FirebaseRemoteSyncSource(gateway)

            val result = source.pushSnapshot(OwnerId("anonymous-owner"), vehicleSnapshot())

            assertEquals(
                RemoteError.Unauthenticated,
                assertIs<Outcome.Err<RemoteError>>(result).error,
            )
            assertEquals(1, gateway.tokenRefreshCount)
            assertEquals(2, gateway.writes.size)
        }

    @Test
    fun unauthenticatedPullRefreshesTheTokenAndRetriesOnce() =
        runTest {
            val entityId = EntityId("123e4567-e89b-42d3-a456-426614174000")
            val serverUpdatedAt = Instant.fromEpochMilliseconds(1_767_225_600_000L)
            val gateway =
                RecordingFirestoreGateway(
                    documents = listOf(vehicleDocument(entityId.value, serverUpdatedAt)),
                    queryFailures = mutableListOf(FirestoreGatewayFailure.UNAUTHENTICATED),
                )
            val source = FirebaseRemoteSyncSource(gateway)

            val result =
                source.pullChanges(
                    ownerId = OwnerId("anonymous-owner"),
                    entityType = EntityType.VEHICLE,
                    cursor = RemoteCursor(Instant.fromEpochMilliseconds(0), null),
                    limit = 50,
                )

            assertEquals(
                entityId,
                assertIs<Outcome.Ok<RemotePage>>(result)
                    .value
                    .items
                    .single()
                    .documentId,
            )
            assertEquals(1, gateway.tokenRefreshCount)
            assertEquals(2, gateway.queries.size)
        }

    @Test
    fun failedRefreshOnPushReturnsUnauthenticatedWithoutRetryingTheWrite() =
        runTest {
            val gateway =
                RecordingFirestoreGateway(
                    writeFailures = mutableListOf(FirestoreGatewayFailure.UNAUTHENTICATED),
                    refreshFailures = mutableListOf(FirestoreGatewayFailure.UNAUTHENTICATED),
                )
            val source = FirebaseRemoteSyncSource(gateway)

            val result = source.pushSnapshot(OwnerId("anonymous-owner"), vehicleSnapshot())

            assertEquals(
                RemoteError.Unauthenticated,
                assertIs<Outcome.Err<RemoteError>>(result).error,
            )
            assertEquals(1, gateway.tokenRefreshCount)
            assertEquals(1, gateway.writes.size)
        }

    @Test
    fun failedRefreshOnPullReturnsUnauthenticatedWithoutRetryingTheQuery() =
        runTest {
            val gateway =
                RecordingFirestoreGateway(
                    queryFailures = mutableListOf(FirestoreGatewayFailure.UNAUTHENTICATED),
                    refreshFailures = mutableListOf(FirestoreGatewayFailure.UNAUTHENTICATED),
                )
            val source = FirebaseRemoteSyncSource(gateway)

            val result =
                source.pullChanges(
                    ownerId = OwnerId("anonymous-owner"),
                    entityType = EntityType.VEHICLE,
                    cursor = RemoteCursor.INITIAL,
                    limit = 50,
                )

            assertEquals(
                RemoteError.Unauthenticated,
                assertIs<Outcome.Err<RemoteError>>(result).error,
            )
            assertEquals(1, gateway.tokenRefreshCount)
            assertEquals(1, gateway.queries.size)
        }

    @Test
    fun firestoreFailuresMapToTheExactRemoteErrorLeaves() =
        runTest {
            val cases =
                listOf(
                    FirestoreGatewayFailure.UNAVAILABLE to RemoteError.Unavailable,
                    FirestoreGatewayFailure.DEADLINE_EXCEEDED to RemoteError.DeadlineExceeded,
                    FirestoreGatewayFailure.PERMISSION_DENIED to RemoteError.PermissionDenied,
                    FirestoreGatewayFailure.INVALID_ARGUMENT to RemoteError.InvalidArgument,
                    FirestoreGatewayFailure.NOT_FOUND to RemoteError.NotFound,
                    FirestoreGatewayFailure.UNKNOWN to RemoteError.Unknown,
                )

            cases.forEach { (failure, expected) ->
                val gateway = RecordingFirestoreGateway(writeFailures = mutableListOf(failure))

                val result =
                    FirebaseRemoteSyncSource(gateway)
                        .pushSnapshot(OwnerId("anonymous-owner"), vehicleSnapshot())

                assertEquals(expected, assertIs<Outcome.Err<RemoteError>>(result).error)
            }
        }

    @Test
    fun aNonProviderThrowableFromHandleAcquisitionBecomesUnknownInsteadOfEscaping() =
        runTest {
            val result =
                try {
                    runProviderRefresh<Unit> { throw IllegalStateException("handle acquisition failed") }
                } catch (failure: FirestoreGatewayException) {
                    failure.failure
                }

            assertEquals(FirestoreGatewayFailure.UNKNOWN, result)
        }

    @Test
    fun cancellationDuringRefreshPropagatesInsteadOfBecomingARemoteError() =
        runTest {
            assertFailsWith<CancellationException> {
                runProviderRefresh<Unit> { throw CancellationException("refresh cancelled") }
            }
        }

    @Test
    fun emptyPullKeepsTheInputCursorAndReportsNoMoreItems() =
        runTest {
            val cursor =
                RemoteCursor(
                    lastServerUpdatedAt = Instant.fromEpochMilliseconds(1_700_000_030_000L),
                    lastDocumentId = EntityId("123e4567-e89b-42d3-a456-426614174000"),
                )

            val page =
                assertIs<Outcome.Ok<RemotePage>>(
                    FirebaseRemoteSyncSource(RecordingFirestoreGateway()).pullChanges(
                        ownerId = OwnerId("anonymous-owner"),
                        entityType = EntityType.VEHICLE,
                        cursor = cursor,
                        limit = 200,
                    ),
                ).value

            assertEquals(emptyList(), page.items)
            assertEquals(cursor, page.nextCursor)
            assertEquals(false, page.hasMore)
        }

    @Test
    fun nonEmptyPullUsesTheLastItemAsCursorWhenTheFirstItemSharesTheInputTimestamp() =
        runTest {
            val sharedTimestamp = Instant.fromEpochMilliseconds(1_700_000_030_000L)
            val inputId = EntityId("123e4567-e89b-42d3-a456-426614174000")
            val firstId = EntityId("123e4567-e89b-42d3-a456-426614174001")
            val lastId = EntityId("123e4567-e89b-42d3-a456-426614174002")
            val gateway =
                RecordingFirestoreGateway(
                    documents =
                        listOf(
                            vehicleDocument(firstId.value, sharedTimestamp),
                            vehicleDocument(lastId.value, sharedTimestamp),
                        ),
                )

            val page =
                assertIs<Outcome.Ok<RemotePage>>(
                    FirebaseRemoteSyncSource(gateway).pullChanges(
                        ownerId = OwnerId("anonymous-owner"),
                        entityType = EntityType.VEHICLE,
                        cursor = RemoteCursor(sharedTimestamp, inputId),
                        limit = 2,
                    ),
                ).value

            assertEquals(RemoteCursor(sharedTimestamp, lastId), page.nextCursor)
            assertEquals(true, page.hasMore)
            assertEquals(inputId.value, gateway.queries.single().afterDocumentId)
        }
}

internal class RecordingFirestoreGateway(
    private val serverUpdatedAt: Instant = Instant.fromEpochMilliseconds(0),
    private val documents: List<FirestoreDocument> = emptyList(),
    private val writeFailures: MutableList<FirestoreGatewayFailure> = mutableListOf(),
    private val queryFailures: MutableList<FirestoreGatewayFailure> = mutableListOf(),
    private val refreshFailures: MutableList<FirestoreGatewayFailure> = mutableListOf(),
) : FirestoreGateway {
    var memoryOnlyConfigurationCount = 0
    var tokenRefreshCount = 0
    val writes = mutableListOf<FirestoreWrite>()
    val queries = mutableListOf<FirestoreQuery>()

    override fun configureMemoryOnlyCache() {
        memoryOnlyConfigurationCount += 1
    }

    override suspend fun writeDocument(write: FirestoreWrite): Instant {
        writes += write
        writeFailures.removeFirstOrNull()?.let { failure ->
            throw FirestoreGatewayException(failure)
        }
        return serverUpdatedAt
    }

    override suspend fun queryDocuments(query: FirestoreQuery): List<FirestoreDocument> {
        queries += query
        queryFailures.removeFirstOrNull()?.let { failure ->
            throw FirestoreGatewayException(failure)
        }
        return documents
    }

    override suspend fun refreshAuthToken() {
        tokenRefreshCount += 1
        refreshFailures.removeFirstOrNull()?.let { failure ->
            throw FirestoreGatewayException(failure)
        }
    }
}

private fun vehicleSnapshot(): EntitySnapshot {
    val entityId = EntityId("123e4567-e89b-42d3-a456-426614174000")
    return EntitySnapshot(
        entityType = EntityType.VEHICLE,
        entityId = entityId,
        schemaVersion = 1,
        json = vehicleJson(entityId.value),
    )
}

private fun vehicleDocument(
    id: String,
    serverUpdatedAt: Instant,
): FirestoreDocument =
    FirestoreDocument(
        id = id,
        fields =
            mapOf(
                "id" to id,
                "ownerId" to "anonymous-owner",
                "name" to "Roadster",
                "initialOdometerKm" to 0L,
                "brand" to null,
                "model" to null,
                "fuelType" to "GASOLINE",
                "createdAt" to 1_700_000_000_000L,
                "updatedAt" to serverUpdatedAt.toEpochMilliseconds(),
                "deleted" to false,
                "deletedAt" to null,
                "schemaVersion" to 1L,
            ),
    )

private fun vehicleJson(
    id: String,
    updatedAt: Long = 1_700_000_000_000L,
): String =
    """
    {
      "entityType":"VEHICLE",
      "id":"$id",
      "ownerId":"anonymous-owner",
      "name":"Roadster",
      "initialOdometerKm":0,
      "brand":null,
      "model":null,
      "fuelType":"GASOLINE",
      "createdAt":1700000000000,
      "updatedAt":$updatedAt,
      "deleted":false,
      "deletedAt":null,
      "schemaVersion":1
    }
    """.trimIndent()

private fun fuelEntryDocument(
    id: String,
    serverUpdatedAt: Instant,
): FirestoreDocument =
    FirestoreDocument(
        id = id,
        fields =
            mapOf(
                "id" to id,
                "ownerId" to "anonymous-owner",
                "vehicleId" to "123e4567-e89b-42d3-a456-426614174000",
                "date" to 1_700_000_000_000L,
                "odometerKm" to 100L,
                "litersScaled" to 50_000L,
                "pricePerLiterScaled" to 1_500L,
                "totalCostMinor" to 7_500L,
                "currency" to "EUR",
                "isFullTank" to true,
                "hasMissedEntries" to false,
                "odometerInconsistent" to false,
                "notes" to null,
                "createdAt" to 1_700_000_000_000L,
                "updatedAt" to serverUpdatedAt.toEpochMilliseconds(),
                "deleted" to false,
                "deletedAt" to null,
                "schemaVersion" to 1L,
            ),
    )

private fun fuelEntryRemoteJson(
    id: String,
    updatedAt: Long,
): String =
    """
    {
      "id":"$id",
      "ownerId":"anonymous-owner",
      "vehicleId":"123e4567-e89b-42d3-a456-426614174000",
      "date":1700000000000,
      "odometerKm":100,
      "litersScaled":50000,
      "pricePerLiterScaled":1500,
      "totalCostMinor":7500,
      "currency":"EUR",
      "isFullTank":true,
      "hasMissedEntries":false,
      "odometerInconsistent":false,
      "notes":null,
      "createdAt":1700000000000,
      "updatedAt":$updatedAt,
      "deleted":false,
      "deletedAt":null,
      "schemaVersion":1
    }
    """.trimIndent()

private fun vehicleRemoteJson(
    id: String,
    updatedAt: Long = 1_700_000_000_000L,
): String =
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
      "updatedAt":$updatedAt,
      "deleted":false,
      "deletedAt":null,
      "schemaVersion":1
    }
    """.trimIndent()
