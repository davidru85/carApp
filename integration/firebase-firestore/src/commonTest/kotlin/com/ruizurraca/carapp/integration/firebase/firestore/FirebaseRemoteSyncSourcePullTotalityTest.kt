package com.ruizurraca.carapp.integration.firebase.firestore

import com.ruizurraca.carapp.core.common.EntityType
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.toEpochMicroseconds
import com.ruizurraca.carapp.core.model.OwnerId
import com.ruizurraca.carapp.core.sync.RemoteCursor
import com.ruizurraca.carapp.core.sync.RemotePage
import com.ruizurraca.carapp.core.sync.UNSUPPORTED_PROVIDER_VALUE_KEY
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant

/**
 * `E3-20` / `D-208`: the pull boundary's conversion of provider values is total.
 *
 * A value the integration cannot represent in JSON used to throw, which failed the whole page as
 * `RemoteError.InvalidArgument` and left the cursor where it was, so one document stalled the pull for
 * ever. The integration now carries such a value as the reserved marker in its own position and keeps
 * every other field verbatim; it classifies nothing (`D-170`), and `:core:sync` quarantines the marked
 * document. The provider types themselves (`GeoPoint`, `Blob`, `DocumentReference`) exist only behind a
 * live provider, so these tests reach the common conversion through the `FirestoreGateway` seam with a
 * value type it cannot represent.
 */
class FirebaseRemoteSyncSourcePullTotalityTest {
    @Test
    fun anUnsupportedTopLevelValueBecomesAMarkerAndThePageSucceeds() =
        runTest {
            val json = pulledJson(vehicleFields + ("name" to UnrepresentableProviderValue()))

            assertEquals(marker("UnrepresentableProviderValue"), json["name"])
            assertEquals(JsonPrimitive("GASOLINE"), json["fuelType"], "every other field stays verbatim")
            assertEquals(JsonPrimitive(DOCUMENT_ID), json["id"])
        }

    @Test
    fun anUnsupportedValueInsideAMapOrAListBecomesAMarkerInPlace() =
        runTest {
            val json =
                pulledJson(
                    vehicleFields +
                        ("brand" to mapOf("kept" to "value", "nested" to UnrepresentableProviderValue())) +
                        ("model" to listOf("kept", UnrepresentableProviderValue())),
                )

            assertEquals(
                buildJsonObject {
                    put("kept", "value")
                    put("nested", marker("UnrepresentableProviderValue"))
                },
                json["brand"],
            )
            assertEquals(
                buildJsonArray {
                    add(JsonPrimitive("kept"))
                    add(marker("UnrepresentableProviderValue"))
                },
                json["model"],
            )
        }

    @Test
    fun aMapWithANonStringKeyBecomesOneMarker() =
        runTest {
            val json = pulledJson(vehicleFields + ("brand" to mapOf(1L to "value")))

            assertEquals(marker(NON_STRING_KEY_MAP), json["brand"])
        }

    private suspend fun pulledJson(fields: Map<String, Any?>): JsonObject {
        val document =
            FirestoreDocument(
                id = DOCUMENT_ID,
                orderingUpdatedAtMicros = SERVER_UPDATED_AT.toEpochMicroseconds(),
                fields = fields,
            )
        val result =
            FirebaseRemoteSyncSource(RecordingFirestoreGateway(documents = listOf(document))).pullChanges(
                ownerId = OwnerId("anonymous-owner"),
                entityType = EntityType.VEHICLE,
                cursor = RemoteCursor.INITIAL,
                limit = 200,
            )
        val item =
            assertIs<Outcome.Ok<RemotePage>>(
                result,
                "an unsupported value MUST NOT fail the page",
            ).value.items.single()
        assertEquals(SERVER_UPDATED_AT, item.serverUpdatedAt)
        return Json.parseToJsonElement(item.rawJson).jsonObject
    }

    private fun marker(typeName: String): JsonElement =
        buildJsonObject { put(UNSUPPORTED_PROVIDER_VALUE_KEY, typeName) }

    /** Stands in for a provider value type with no JSON form, such as `GeoPoint` or `Blob`. */
    private class UnrepresentableProviderValue

    private companion object {
        const val DOCUMENT_ID = "vehicle-unsupported"
        const val NON_STRING_KEY_MAP = "MapWithNonStringKey"
        val SERVER_UPDATED_AT: Instant = Instant.fromEpochMilliseconds(1_767_225_600_000L)
        val vehicleFields: Map<String, Any?> =
            mapOf(
                "id" to DOCUMENT_ID,
                "ownerId" to "anonymous-owner",
                "name" to "Roadster",
                "initialOdometerKm" to 0L,
                "brand" to null,
                "model" to null,
                "fuelType" to "GASOLINE",
                "createdAt" to 1_700_000_000_000L,
                "updatedAt" to SERVER_UPDATED_AT.toEpochMilliseconds(),
                "deleted" to false,
                "deletedAt" to null,
                "schemaVersion" to 1L,
            )
    }
}
