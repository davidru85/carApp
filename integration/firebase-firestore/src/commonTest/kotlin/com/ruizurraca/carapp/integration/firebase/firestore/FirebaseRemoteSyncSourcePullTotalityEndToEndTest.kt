package com.ruizurraca.carapp.integration.firebase.firestore

import com.ruizurraca.carapp.core.common.AppClock
import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.ConnectivityObserver
import com.ruizurraca.carapp.core.common.EntityType
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.OwnerContext
import com.ruizurraca.carapp.core.common.SyncTrigger
import com.ruizurraca.carapp.core.common.UuidGenerator
import com.ruizurraca.carapp.core.common.toEpochMicroseconds
import com.ruizurraca.carapp.core.database.SyncDatabaseAccess
import com.ruizurraca.carapp.core.database.createStagedDatabaseFactory
import com.ruizurraca.carapp.core.model.OwnerId
import com.ruizurraca.carapp.core.sync.QuarantineRecord
import com.ruizurraca.carapp.core.sync.UNSUPPORTED_PROVIDER_VALUE_KEY
import com.ruizurraca.carapp.core.sync.createSyncController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * `E3-20` acceptance end to end: the production integration, the real sync engine and the real
 * database (`D-208`).
 *
 * Before the fix, a document carrying a provider value the integration could not represent failed the
 * whole page as `RemoteError.InvalidArgument`; `failPullCycle` kept the stored cursor, so every later
 * cycle re-requested the same page and the pull stalled for ever. After it, the document reaches the
 * engine with the reserved marker, is stored as a `MalformedPayload` quarantine record, and the
 * cursor advances past it in the same transaction.
 *
 * The controller runs on its own [Job], joined before the database closes, because the driver works on
 * a real executor.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FirebaseRemoteSyncSourcePullTotalityEndToEndTest {
    @Test
    fun aDocumentWithAnUnsupportedValueIsQuarantinedAndTheCursorAdvances() =
        assertQuarantinedAndCursorAdvanced(UnrepresentableProviderValue(), "UnrepresentableProviderValue")

    @Test
    fun aDocumentWithNaNIsQuarantinedWithAMarkerAndTheCursorAdvances() =
        assertQuarantinedAndCursorAdvanced(Double.NaN, "Double")

    @Test
    fun aDocumentWithPositiveInfinityIsQuarantinedWithAMarkerAndTheCursorAdvances() =
        assertQuarantinedAndCursorAdvanced(Double.POSITIVE_INFINITY, "Double")

    @Test
    fun aDocumentWithNegativeInfinityIsQuarantinedWithAMarkerAndTheCursorAdvances() =
        assertQuarantinedAndCursorAdvanced(Double.NEGATIVE_INFINITY, "Double")

    private fun assertQuarantinedAndCursorAdvanced(
        providerValue: Any?,
        expectedTypeName: String,
    ) = runTest {
        val reportedErrors = mutableListOf<AppError>()
        val reportedQuarantines = mutableListOf<QuarantineRecord>()
        val handle = createStagedDatabaseFactory().create()
        val controllerJob = Job()
        try {
            val gateway =
                RecordingFirestoreGateway(documents = listOf(documentWithUnsupportedValue(providerValue)))
            val controller =
                createSyncController(
                    scope = CoroutineScope(coroutineContext + controllerJob),
                    databaseAccess = SyncDatabaseAccess(handle.database),
                    ownerContext = StaticOwnerContext(OwnerId(OWNER_ID)),
                    connectivity = StaticConnectivity,
                    remote = FirebaseRemoteSyncSource(gateway),
                    clock = StaticClock,
                    uuidGenerator = StaticUuidGenerator,
                    adoption = { Outcome.Ok(Unit) },
                    onPoisoned = { error, _ -> reportedErrors += error },
                    onQuarantined = reportedQuarantines::add,
                    isDebugBuild = false,
                )

            val outcome = controller.sync(SyncTrigger.PullToRefresh)
            advanceUntilIdle()

            val lines = SyncDatabaseAccess(handle.database).debugLines()
            assertEquals(Outcome.Ok(Unit), outcome, "the page no longer fails, so the cycle succeeds")
            assertTrue(
                "quarantine type=VEHICLE id=$DOCUMENT_ID reason=MalformedPayload schema=1 " +
                    "at=${SERVER_UPDATED_AT.toEpochMilliseconds()}" in lines,
                "the document is quarantined as MalformedPayload. Saw $lines",
            )
            assertTrue(
                "cursor type=VEHICLE at=${SERVER_UPDATED_AT.toEpochMilliseconds()} id=$DOCUMENT_ID" in lines,
                "the vehicle cursor advanced past the quarantined document. Saw $lines",
            )
            assertEquals(emptyList(), reportedErrors, "a quarantined document is not an error report")

            val quarantined = reportedQuarantines.single { it.entityType == EntityType.VEHICLE }
            assertEquals(
                buildJsonObject {
                    put(UNSUPPORTED_PROVIDER_VALUE_KEY, JsonPrimitive(expectedTypeName))
                },
                Json.parseToJsonElement(quarantined.rawJson).jsonObject["brand"],
                "the committed quarantine record carries the reserved marker at the original position",
            )
        } finally {
            controllerJob.cancelAndJoin()
            handle.close()
        }
    }

    private fun documentWithUnsupportedValue(providerValue: Any?): FirestoreDocument =
        FirestoreDocument(
            id = DOCUMENT_ID,
            orderingUpdatedAtMicros = SERVER_UPDATED_AT.toEpochMicroseconds(),
            fields =
                mapOf(
                    "id" to DOCUMENT_ID,
                    "ownerId" to OWNER_ID,
                    "name" to "Roadster",
                    "initialOdometerKm" to 0L,
                    "brand" to providerValue,
                    "model" to null,
                    "fuelType" to "GASOLINE",
                    "createdAt" to 1_700_000_000_000L,
                    "updatedAt" to SERVER_UPDATED_AT.toEpochMilliseconds(),
                    "deleted" to false,
                    "deletedAt" to null,
                    "schemaVersion" to 1L,
                ),
        )

    /** Stands in for a provider value type with no JSON form, such as `GeoPoint` or `Blob`. */
    private class UnrepresentableProviderValue

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

    private companion object {
        const val DOCUMENT_ID = "123e4567-e89b-42d3-a456-426614174000"
        const val OWNER_ID = "anonymous-owner"
        val SERVER_UPDATED_AT: Instant = Instant.fromEpochMilliseconds(1_767_225_600_000L)
    }
}
