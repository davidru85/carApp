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
                    "mismatched ownerId" to payloadWith(mapOf("ownerId" to "\"$OTHER_OWNER_ID\"")),
                    "mismatched schemaVersion" to payloadWith(mapOf("schemaVersion" to "2")),
                    "createdAt is an object" to payloadWith(mapOf("createdAt" to "{}")),
                    "createdAt is a string" to payloadWith(mapOf("createdAt" to "\"yesterday\"")),
                    "deletedAt is an object" to payloadWith(mapOf("deletedAt" to "{\"at\":1}")),
                    // The conversion does not validate the per-entity schema, so the `date` rule of `§10`
                    // applies to any payload that carries the key, including this Vehicle payload.
                    "date is a numeric string" to payloadWith(mapOf("date" to "\"1700000000000\"")),
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

    /**
     * `E3-19` review correction 3: `Json.parseToJsonElement` accepts any unquoted token as a literal,
     * kotlinx `booleanOrNull` ignores case and kotlinx `longOrNull` accepts leading zeros and exponents.
     * Before the correction, each token below was written as a provider boolean, integer or timestamp,
     * and `01` and `1e0` passed the `schemaVersion` identity check. RFC 8259 defines none of them as a
     * boolean or an integer token, so each MUST fail closed with zero writes. Every accepted token is
     * collected, so one run names all of them.
     */
    @Test
    fun aNonCanonicalJsonTokenFailsClosedWithoutWritingAnything() =
        runTest {
            val cases =
                listOf(
                    "deleted is an upper-case TRUE token" to payloadWith(mapOf("deleted" to "TRUE")),
                    "deleted is a capitalized False token" to payloadWith(mapOf("deleted" to "False")),
                    "initialOdometerKm has a leading zero" to payloadWith(mapOf("initialOdometerKm" to "007")),
                    "initialOdometerKm has an exponent" to payloadWith(mapOf("initialOdometerKm" to "1e3")),
                    "schemaVersion has a leading zero" to payloadWith(mapOf("schemaVersion" to "01")),
                    "schemaVersion has an exponent" to payloadWith(mapOf("schemaVersion" to "1e0")),
                    "createdAt has an exponent" to payloadWith(mapOf("createdAt" to "17E11")),
                    "createdAt has a leading zero" to payloadWith(mapOf("createdAt" to "01700000000000")),
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
                "every non-canonical JSON token MUST fail closed with zero writes",
            )
        }

    /**
     * `E3-19` review correction 3: the strict token readers still write every canonical token the `§8`
     * producer emits — the lowercase `true`, a multi-digit integer and an epoch-millisecond integer on a
     * tombstone — with the provider type `§10` expects.
     */
    @Test
    fun aCanonicalTombstonePayloadStillWritesStrictlyTypedValues() =
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
                            json =
                                payloadWith(
                                    overrides =
                                        mapOf(
                                            "initialOdometerKm" to "120000",
                                            "deleted" to "true",
                                            "deletedAt" to "1700000000001",
                                        ),
                                ),
                        ),
                )

            assertIs<Outcome.Ok<*>>(result)
            val fields = gateway.writes.single().fields
            assertEquals(FirestoreLong(120_000), fields.getValue("initialOdometerKm"))
            assertEquals(FirestoreBoolean(true), fields.getValue("deleted"))
            assertEquals(FirestoreTimestamp(1_700_000_000_001), fields.getValue("deletedAt"))
            assertEquals(FirestoreLong(1), fields.getValue("schemaVersion"))
        }

    @Test
    fun aNullUpdatedAtIsAlwaysReplacedWithTheServerTimestamp() =
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
                            json = payloadWith(overrides = mapOf("updatedAt" to "null")),
                        ),
                )

            assertIs<Outcome.Ok<*>>(result)
            assertEquals(
                FirestoreServerTimestamp,
                gateway.writes
                    .single()
                    .fields
                    .getValue("updatedAt"),
                "the payload value of updatedAt MUST be ignored and replaced with the server timestamp",
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
private const val OTHER_OWNER_ID = "other-owner"
