package com.ruizurraca.carapp.integration.firebase.firestore

import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.RemoteError
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.OwnerId
import com.ruizurraca.carapp.core.sync.EntitySnapshot
import com.ruizurraca.carapp.core.sync.EntityType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * `E3-19`: every field read on the push boundary is total.
 *
 * `EntitySnapshot.toFirestoreWrite` used to read the payload identity fields through
 * `JsonObject.getValue`, which throws `NoSuchElementException` for a missing key. `pushSnapshot` caught
 * only `IllegalArgumentException` (which a wrong-typed `JsonElement.jsonPrimitive` read throws), so the
 * missing-key failure escaped the closed `Outcome` API, reached the engine's generic cycle catch as
 * `UnexpectedError`, and left the entity row `SYNCING` where it repeated every cycle. A missing or
 * wrong-typed key is a producer defect under `docs/CONTRACTS.md §8`, but the boundary still MUST
 * classify it as a closed `RemoteError.InvalidArgument` so the push path poisons the row instead of
 * stranding it.
 */
class FirebaseRemoteSyncSourcePushPayloadTotalityTest {
    @Test
    fun everyMalformedPayloadFieldFailsClosedWithoutWritingAnything() =
        runTest {
            val cases =
                listOf(
                    "missing id" to payloadWithout("id"),
                    "id is an object" to payloadWith(mapOf("id" to "{}")),
                    "id is null" to payloadWith(mapOf("id" to "null")),
                    "missing ownerId" to payloadWithout("ownerId"),
                    "ownerId is a number" to payloadWith(mapOf("ownerId" to "7")),
                    "ownerId is null" to payloadWith(mapOf("ownerId" to "null")),
                    "missing schemaVersion" to payloadWithout("schemaVersion"),
                    "schemaVersion is an object" to payloadWith(mapOf("schemaVersion" to "{}")),
                    "schemaVersion is null" to payloadWith(mapOf("schemaVersion" to "null")),
                    "missing entityType" to payloadWithout("entityType"),
                    "entityType is an object" to payloadWith(mapOf("entityType" to "{}")),
                    "mismatched id" to payloadWith(mapOf("id" to "\"$OTHER_VEHICLE_ID\"")),
                    "mismatched entityType" to payloadWith(mapOf("entityType" to "\"FUEL_ENTRY\"")),
                    "createdAt is an object" to payloadWith(mapOf("createdAt" to "{}")),
                    "createdAt is a string" to payloadWith(mapOf("createdAt" to "\"yesterday\"")),
                    "deletedAt is an object" to payloadWith(mapOf("deletedAt" to "{\"at\":1}")),
                    "root is an array" to "[1,2,3]",
                    "root is a scalar" to "\"vehicle\"",
                    "root is not JSON" to "{oops",
                    "brand is an array" to payloadWith(mapOf("brand" to "[\"Acme\"]")),
                    "initialOdometerKm is a fraction" to payloadWith(mapOf("initialOdometerKm" to "1.5")),
                )

            cases.forEach { (description, payload) ->
                val gateway = RecordingFirestoreGateway()
                val source = FirebaseRemoteSyncSource(gateway)

                val result =
                    source.pushSnapshot(
                        ownerId = OwnerId(OWNER_ID),
                        snapshot =
                            EntitySnapshot(
                                entityType = EntityType.VEHICLE,
                                entityId = EntityId(VEHICLE_ID),
                                schemaVersion = 1,
                                json = payload,
                            ),
                    )

                assertEquals(
                    RemoteError.InvalidArgument,
                    assertIs<Outcome.Err<RemoteError>>(result, "$description must fail closed").error,
                    "$description must be classified as a closed RemoteError",
                )
                assertTrue(
                    gateway.writes.isEmpty(),
                    "$description must be rejected before anything is written, but ${gateway.writes.size} write(s) ran",
                )
            }
        }

    /**
     * `E3-19` review correction 1: a value whose JSON type is wrong MUST fail closed even when its
     * content would coerce to the expected value. Before the correction, `"schemaVersion":"1"` passed
     * the identity check and was written as a provider string, an unquoted `entityType` literal matched
     * the entity type name, and a numeric string in `createdAt` or `deletedAt` was coerced into a
     * provider timestamp. Every accepted shape is collected, so one run names all of them.
     */
    @Test
    fun aCoercibleValueOfTheWrongJsonTypeFailsClosedWithoutWritingAnything() =
        runTest {
            val cases =
                listOf(
                    "schemaVersion is a numeric string" to payloadWith(mapOf("schemaVersion" to "\"1\"")),
                    "entityType is an unquoted literal" to payloadWith(mapOf("entityType" to "VEHICLE")),
                    "createdAt is a numeric string" to payloadWith(mapOf("createdAt" to "\"1700000000000\"")),
                    "deletedAt is a numeric string" to payloadWith(mapOf("deletedAt" to "\"1700000000000\"")),
                )

            val accepted =
                cases.mapNotNull { (description, payload) ->
                    val gateway = RecordingFirestoreGateway()
                    val result =
                        FirebaseRemoteSyncSource(gateway).pushSnapshot(
                            ownerId = OwnerId(OWNER_ID),
                            snapshot =
                                EntitySnapshot(
                                    entityType = EntityType.VEHICLE,
                                    entityId = EntityId(VEHICLE_ID),
                                    schemaVersion = 1,
                                    json = payload,
                                ),
                        )
                    val failedClosed =
                        result == Outcome.Err(RemoteError.InvalidArgument) && gateway.writes.isEmpty()
                    description.takeUnless { failedClosed }
                }

            assertEquals(
                emptyList<String>(),
                accepted,
                "every coercible value of the wrong JSON type MUST fail closed with zero writes",
            )
        }

    @Test
    fun aWellFormedPayloadStillWritesTheDocument() =
        runTest {
            val gateway = RecordingFirestoreGateway()
            val source = FirebaseRemoteSyncSource(gateway)

            val result =
                source.pushSnapshot(
                    ownerId = OwnerId(OWNER_ID),
                    snapshot =
                        EntitySnapshot(
                            entityType = EntityType.VEHICLE,
                            entityId = EntityId(VEHICLE_ID),
                            schemaVersion = 1,
                            json = payloadWith(),
                        ),
                )

            assertIs<Outcome.Ok<*>>(result)
            assertEquals(
                listOf("users/$OWNER_ID/vehicles/$VEHICLE_ID"),
                gateway.writes.map { it.path },
                "the totality hardening must not reject a canonical payload",
            )
        }
}

private fun payloadWithout(field: String): String = payloadWith(omissions = setOf(field))

private fun payloadWith(
    overrides: Map<String, String> = emptyMap(),
    omissions: Set<String> = emptySet(),
): String {
    val fields =
        linkedMapOf(
            "entityType" to "\"VEHICLE\"",
            "id" to "\"$VEHICLE_ID\"",
            "ownerId" to "\"$OWNER_ID\"",
            "name" to "\"Roadster\"",
            "initialOdometerKm" to "0",
            "brand" to "null",
            "model" to "null",
            "fuelType" to "\"GASOLINE\"",
            "createdAt" to "1700000000000",
            "updatedAt" to "1700000000000",
            "deleted" to "false",
            "deletedAt" to "null",
            "schemaVersion" to "1",
        ).apply {
            putAll(overrides)
            omissions.forEach { remove(it) }
        }
    return fields.entries.joinToString(prefix = "{", postfix = "}", separator = ",") { (field, value) ->
        "\"$field\":$value"
    }
}

private const val VEHICLE_ID = "123e4567-e89b-42d3-a456-426614174000"
private const val OTHER_VEHICLE_ID = "123e4567-e89b-42d3-a456-4266141740ff"
private const val OWNER_ID = "anonymous-owner"
