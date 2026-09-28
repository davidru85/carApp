package com.ruizurraca.carapp.integration.firebase.firestore

import com.ruizurraca.carapp.core.common.AppClock
import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.ConnectivityObserver
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.OwnerContext
import com.ruizurraca.carapp.core.common.SyncError
import com.ruizurraca.carapp.core.common.SyncTrigger
import com.ruizurraca.carapp.core.common.UnexpectedError
import com.ruizurraca.carapp.core.common.UuidGenerator
import com.ruizurraca.carapp.core.database.DatabaseMutations
import com.ruizurraca.carapp.core.database.SyncDatabaseAccess
import com.ruizurraca.carapp.core.database.createStagedDatabaseFactory
import com.ruizurraca.carapp.core.model.OwnerId
import com.ruizurraca.carapp.core.sync.createSyncController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * `E3-19` end to end: the production integration, the real sync engine and the real database.
 *
 * This is the acceptance criterion a source-level test cannot express. Before the fix, a malformed
 * payload threw `NoSuchElementException` / `IllegalStateException` out of
 * `FirebaseRemoteSyncSource.pushSnapshot`, escaped the engine's per-row classification, was caught by
 * `drainCycles` as `UnexpectedError`, and left the entity row `SYNCING` where it re-failed every
 * cycle. After the fix the same payload is a closed `RemoteError.InvalidArgument`, which `§6` maps to
 * `SyncError.ValidationRejected` and poisons on the first attempt.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FirebaseRemoteSyncSourcePushTotalityEndToEndTest {
    @Test
    fun aPayloadMissingIdPoisonsTheRowInsteadOfStrandingIt() =
        runTest { assertMalformedPayloadPoisons(vehiclePayload(omit = "id")) }

    @Test
    fun aPayloadMissingOwnerIdPoisonsTheRowInsteadOfStrandingIt() =
        runTest { assertMalformedPayloadPoisons(vehiclePayload(omit = "ownerId")) }

    @Test
    fun aPayloadMissingSchemaVersionPoisonsTheRowInsteadOfStrandingIt() =
        runTest { assertMalformedPayloadPoisons(vehiclePayload(omit = "schemaVersion")) }

    @Test
    fun aWrongTypedOwnerIdPoisonsTheRowInsteadOfStrandingIt() =
        runTest { assertMalformedPayloadPoisons(vehiclePayload(ownerIdValue = "7")) }

    /**
     * Seeds one Vehicle whose outbox payload is [payload], runs exactly one production cycle, and
     * asserts the three observable consequences the story names: the row leaves `SYNCING`, no
     * `UnexpectedError` is reported, and the cycle returns a classified error.
     *
     * `SyncDatabaseAccess.debugLines()` is the reader instead of a generated query: `:core:sync`
     * already exposes the row state and the outbox retry context there, and `:integration:*` MUST NOT
     * reach a generated SQLDelight entity query (`D-38`).
     */
    private suspend fun TestScope.assertMalformedPayloadPoisons(payload: String) {
        val reportedErrors = mutableListOf<AppError>()
        val handle = createStagedDatabaseFactory().create()
        try {
            val database = handle.database
            DatabaseMutations(database).insertVehicle(
                id = VEHICLE_ID,
                ownerId = OWNER_ID,
                name = "Roadster",
                nameFold = "roadster",
                initialOdometerKm = 0,
                brand = null,
                model = null,
                fuelType = "GASOLINE",
                createdAt = 0,
                updatedAt = 0,
                schemaVersion = 1,
                outboxPayload = payload,
            )
            val controller =
                createSyncController(
                    scope = this,
                    databaseAccess = SyncDatabaseAccess(database),
                    ownerContext = StaticOwnerContext(OwnerId(OWNER_ID)),
                    connectivity = StaticConnectivity,
                    remote = FirebaseRemoteSyncSource(RecordingFirestoreGateway()),
                    clock = StaticClock,
                    uuidGenerator = StaticUuidGenerator,
                    adoption = { Outcome.Ok(Unit) },
                    onPoisoned = { error, _ -> reportedErrors += error },
                    onQuarantined = {},
                    isDebugBuild = false,
                )

            val outcome = controller.sync(SyncTrigger.PullToRefresh)
            advanceUntilIdle()

            val debugLines = SyncDatabaseAccess(database).debugLines()
            assertTrue(
                debugLines.any { it == "row type=VEHICLE id=$VEHICLE_ID state=FAILED_POISONED" },
                "the row MUST leave SYNCING; a stranded SYNCING row is the E3-19 defect. Saw $debugLines",
            )
            assertTrue(
                debugLines.any {
                    it.startsWith("outbox ") &&
                        it.contains("id=$VEHICLE_ID") &&
                        it.contains("error=REMOTE.INVALID_ARGUMENT")
                },
                "the retained outbox row MUST carry the originating RemoteError code. Saw $debugLines",
            )
            assertTrue(
                reportedErrors.none { it is UnexpectedError },
                "a malformed payload MUST NOT escape as UnexpectedError, but reported $reportedErrors",
            )
            assertEquals(
                listOf<AppError>(SyncError.ValidationRejected),
                reportedErrors,
                "the §6 mapping of RemoteError.InvalidArgument MUST poison with SyncError.ValidationRejected",
            )
            // A poisoned push row is a per-row classification, not a cycle failure (`§9.3`: only a
            // failed pull sets the cycle's own failure). The cycle therefore still returns `Ok`, and
            // the row's `FAILED_POISONED` state above is what carries the outcome to the user.
            assertEquals(
                Outcome.Ok(Unit),
                outcome,
                "a per-row push poison MUST NOT fail the whole cycle, only the row it classified",
            )
        } finally {
            handle.close()
        }
    }
}

private fun vehiclePayload(
    omit: String? = null,
    ownerIdValue: String = "\"$OWNER_ID\"",
): String {
    val fields =
        linkedMapOf(
            "entityType" to "\"VEHICLE\"",
            "id" to "\"$VEHICLE_ID\"",
            "ownerId" to ownerIdValue,
            "name" to "\"Roadster\"",
            "initialOdometerKm" to "0",
            "brand" to "null",
            "model" to "null",
            "fuelType" to "\"GASOLINE\"",
            "createdAt" to "0",
            "updatedAt" to "0",
            "deleted" to "false",
            "deletedAt" to "null",
            "schemaVersion" to "1",
        ).apply { omit?.let { remove(it) } }
    return fields.entries.joinToString(prefix = "{", postfix = "}", separator = ",") { (field, value) ->
        "\"$field\":$value"
    }
}

private class StaticOwnerContext(
    private val owner: OwnerId,
) : OwnerContext {
    override val current: OwnerId get() = owner

    override fun observe(): Flow<OwnerId> = MutableStateFlow(owner)
}

private object StaticConnectivity : ConnectivityObserver {
    override val isOnline: StateFlow<Boolean> = MutableStateFlow(true)
}

private object StaticClock : AppClock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(0)
}

private object StaticUuidGenerator : UuidGenerator {
    override fun newId(): String = "cycle-1"
}

private const val VEHICLE_ID = "123e4567-e89b-42d3-a456-426614174000"
private const val OWNER_ID = "anonymous-owner"
