package com.ruizurraca.carapp.core.sync

import com.ruizurraca.carapp.core.common.AppClock
import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.CONNECTIVITY_ERROR_CODES
import com.ruizurraca.carapp.core.common.ConnectivityObserver
import com.ruizurraca.carapp.core.common.MAX_RETRYABLE_ATTEMPTS
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.OwnerContext
import com.ruizurraca.carapp.core.common.PersistenceError
import com.ruizurraca.carapp.core.common.RemoteError
import com.ruizurraca.carapp.core.common.SyncError
import com.ruizurraca.carapp.core.common.SyncStatus
import com.ruizurraca.carapp.core.common.SyncTrigger
import com.ruizurraca.carapp.core.common.UuidGenerator
import com.ruizurraca.carapp.core.common.instantFromEpochMicroseconds
import com.ruizurraca.carapp.core.common.toEpochMicroseconds
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.LOCAL_OWNER
import com.ruizurraca.carapp.core.model.OwnerId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultSyncControllerTest {
    @Test
    fun offlineWriteIsBackedUpAfterConnectivityReturns() =
        runTest {
            val fixture = fixture(online = false).withOutbox(vehicleOutbox("vehicle-1"))

            fixture.controller.requestSync(SyncTrigger.AppForeground)
            advanceUntilIdle()
            assertEquals(0, fixture.remote.pushCalls.size)
            assertEquals(SyncStatus.Pending(1), fixture.controller.status.value)

            fixture.connectivity.set(true)
            fixture.controller.requestSync(SyncTrigger.ConnectivityRecovered)
            advanceUntilIdle()

            assertEquals(listOf("vehicle-1"), fixture.remote.pushCalls.map { it.entityId.value })
            assertEquals(SyncStatus.Idle, fixture.controller.status.value)
        }

    @Test
    fun ambiguousResponseRetryDoesNotDuplicateRecords() =
        runTest {
            val fixture = fixture().withOutbox(vehicleOutbox("vehicle-1"))
            fixture.remote.pushResults += Outcome.Err(RemoteError.Unknown)
            fixture.remote.pushResults += ack("vehicle-1")

            fixture.controller.requestSync(SyncTrigger.AppForeground)
            advanceUntilIdle()
            fixture.clock.advanceBy(2_000)
            fixture.controller.requestSync(SyncTrigger.Periodic)
            advanceUntilIdle()

            assertEquals(2, fixture.remote.pushCalls.size)
            assertEquals(setOf("vehicle-1"), fixture.remote.remoteIds)
            assertEquals(0, fixture.persistence.outbox.size)
        }

    @Test
    fun cleanRecoveryRestoresVehiclesAndFuelEntries() =
        runTest {
            val fixture = fixture()
            fixture.remote.pullHandler = { type, _ ->
                when (type) {
                    EntityType.VEHICLE -> page(remoteVehicle("vehicle-1", 1_000))
                    EntityType.FUEL_ENTRY -> page(remoteFuelEntry("entry-1", "vehicle-1", 2_000))
                }
            }

            fixture.controller.requestSync(SyncTrigger.AppForeground)
            advanceUntilIdle()

            assertEquals(setOf("vehicle-1"), fixture.persistence.vehicleIds)
            assertEquals(setOf("entry-1"), fixture.persistence.fuelEntryIds)
            assertTrue(fixture.persistence.visibleFuelEntryIds.contains("entry-1"))
        }

    @Test
    fun coldStartPullsFirstOnlyWhenOwnerRowsAndOutboxAreEmpty() =
        runTest {
            val empty = fixture()
            empty.controller.requestSync(SyncTrigger.AppForeground)
            advanceUntilIdle()
            assertTrue(empty.persistence.calls.indexOf("cursor") < empty.persistence.calls.indexOf("dueOutbox"))

            val populated = fixture()
            populated.persistence.vehicleIds += "local-vehicle"
            populated.controller.requestSync(SyncTrigger.AppForeground)
            advanceUntilIdle()
            assertTrue(populated.persistence.calls.indexOf("dueOutbox") < populated.persistence.calls.indexOf("cursor"))
        }

    @Test
    fun exactTimestampTiePaginatesInDocumentIdOrder() =
        runTest {
            val fixture = fixture()
            val timestamp = 5_000L
            fixture.remote.pullHandler = { type, cursor ->
                if (type == EntityType.FUEL_ENTRY) {
                    page()
                } else {
                    when (cursor.lastDocumentId?.value) {
                        null -> page(remoteVehicle("vehicle-a", timestamp), hasMore = true)
                        "vehicle-a" -> page(remoteVehicle("vehicle-b", timestamp))
                        else -> page()
                    }
                }
            }

            fixture.controller.requestSync(SyncTrigger.AppForeground)
            advanceUntilIdle()

            assertEquals(listOf(null, "vehicle-a"), fixture.remote.vehiclePullCursors.map { it.lastDocumentId?.value })
            assertEquals(setOf("vehicle-a", "vehicle-b"), fixture.persistence.vehicleIds)
        }

    @Test
    fun tombstoneWinsOverAnOlderUpdate() =
        runTest {
            val fixture = fixture()
            fixture.persistence.vehicleServerTimes["vehicle-1"] = 2_000L
            fixture.remote.pullHandler = { type, _ ->
                if (type == EntityType.VEHICLE) page(remoteVehicle("vehicle-1", 1_000, deleted = false)) else page()
            }
            fixture.controller.requestSync(SyncTrigger.AppForeground)
            advanceUntilIdle()
            assertFalse(fixture.persistence.deletedVehicles.contains("vehicle-1"))

            fixture.remote.pullHandler = { type, _ ->
                if (type == EntityType.VEHICLE) page(remoteVehicle("vehicle-1", 3_000, deleted = true)) else page()
            }
            fixture.controller.requestSync(SyncTrigger.PullToRefresh)
            advanceUntilIdle()
            assertTrue(fixture.persistence.deletedVehicles.contains("vehicle-1"))
        }

    @Test
    fun pullOverlapIncludesADocumentBeforeTheStoredCursor() =
        runTest {
            val fixture = fixture()
            fixture.persistence.cursors[EntityType.VEHICLE] = cursor(60_000, "previous")
            fixture.remote.pullHandler = { type, _ ->
                if (type == EntityType.VEHICLE) page(remoteVehicle("vehicle-1", 45_000)) else page()
            }

            fixture.controller.requestSync(SyncTrigger.PullToRefresh)
            advanceUntilIdle()

            assertEquals(
                30_000L,
                fixture.remote.vehiclePullCursors
                    .first()
                    .lastServerUpdatedAt
                    .toEpochMilliseconds(),
            )
            assertNull(
                fixture.remote.vehiclePullCursors
                    .first()
                    .lastDocumentId,
            )
            assertTrue(fixture.persistence.vehicleIds.contains("vehicle-1"))
        }

    @Test
    fun deviceClockAheadDoesNotWinRemoteConflict() =
        runTest {
            val fixture = fixture(now = 3_600_000)
            fixture.persistence.vehicleServerTimes["vehicle-1"] = null
            fixture.remote.pullHandler = { type, _ ->
                if (type == EntityType.VEHICLE) page(remoteVehicle("vehicle-1", 1_000)) else page()
            }

            fixture.controller.requestSync(SyncTrigger.AppForeground)
            advanceUntilIdle()

            assertEquals(1_000L, fixture.persistence.vehicleServerTimes["vehicle-1"])
        }

    @Test
    fun firstSyncOfOneThousandRecordsIsPaginated() =
        runTest {
            val fixture = fixture()
            val documents =
                (1..1_000).map { index ->
                    remoteVehicle("vehicle-${index.toString().padStart(4, '0')}", index.toLong())
                }
            fixture.remote.seedDocuments(EntityType.VEHICLE, documents)

            fixture.controller.requestSync(SyncTrigger.AppForeground)
            advanceUntilIdle()

            assertEquals(1_000, fixture.persistence.vehicleIds.size)
            assertEquals(6, fixture.remote.vehiclePullCursors.size)
        }

    @Test
    fun nonConnectivityFailuresPoisonAtTheCeilingAndManualRetryRevives() =
        runTest {
            val fixture = fixture().withOutbox(vehicleOutbox("vehicle-1"))
            repeat(10) {
                fixture.remote.pushResults += Outcome.Err(RemoteError.Unknown)
                fixture.controller.requestSync(SyncTrigger.PullToRefresh)
                advanceUntilIdle()
                fixture.clock.advanceBy(900_000)
            }

            assertEquals("FAILED_POISONED", fixture.persistence.states.getValue("vehicle-1"))
            assertIs<SyncStatus.Failed>(fixture.controller.status.value)

            assertIs<Outcome.Ok<Unit>>(fixture.controller.retryFailed())
            assertEquals(
                0,
                fixture.persistence.outbox
                    .single()
                    .attemptCount,
            )
            assertEquals("PENDING", fixture.persistence.states.getValue("vehicle-1"))
        }

    @Test
    fun repeatedAuthenticationFailuresNeverPoisonAndKeepTheAttemptCount() =
        runTest {
            // `§6` is normative: `Unauthenticated` retries after a valid auth session with
            // `attemptCount` unchanged and never poisons, so no number of consecutive auth failures
            // may reach FAILED_POISONED or raise the count. The row is a non-connectivity retryable
            // failure, which `§7` renders FAILED_RETRYABLE and `§9.9` therefore reports as Failed.
            val fixture = fixture().withOutbox(vehicleOutbox("vehicle-1"))
            repeat(MAX_RETRYABLE_ATTEMPTS + 5) {
                fixture.remote.pushResults += Outcome.Err(RemoteError.Unauthenticated)
                fixture.controller.requestSync(SyncTrigger.PullToRefresh)
                advanceUntilIdle()
                fixture.clock.advanceBy(900_000)
            }

            assertEquals(
                0,
                fixture.persistence.outbox
                    .single()
                    .attemptCount,
                "Unauthenticated MUST NOT consume the retry budget (§6)",
            )
            assertEquals("FAILED_RETRYABLE", fixture.persistence.states.getValue("vehicle-1"))
            val status = assertIs<SyncStatus.Failed>(fixture.controller.status.value)
            assertEquals(1, status.retryableCount)
            assertEquals(0, status.poisonedCount)
        }

    @Test
    fun anAuthenticationFailureIsFollowedByASuccessfulPushWithoutARaisedCount() =
        runTest {
            val fixture = fixture().withOutbox(vehicleOutbox("vehicle-1"))
            fixture.remote.pushResults += Outcome.Err(RemoteError.Unauthenticated)
            fixture.controller.requestSync(SyncTrigger.PullToRefresh)
            advanceUntilIdle()

            assertEquals(
                0,
                fixture.persistence.outbox
                    .single()
                    .attemptCount,
            )
            assertEquals("FAILED_RETRYABLE", fixture.persistence.states.getValue("vehicle-1"))

            // A later successful push confirms the row normally; the auth failure never raised the count.
            fixture.clock.advanceBy(900_000)
            fixture.remote.pushResults += ack("vehicle-1")
            fixture.controller.requestSync(SyncTrigger.PullToRefresh)
            advanceUntilIdle()

            assertEquals("SYNCED", fixture.persistence.states.getValue("vehicle-1"))
            assertEquals(emptyList(), fixture.persistence.outbox)
        }

    @Test
    fun authenticationRetryDoesNotWeakenUnknownOrValidationPoisoning() =
        runTest {
            // Over-correction guard: `Unknown` still consumes the budget and poisons at the ceiling.
            val ambiguous = fixture().withOutbox(vehicleOutbox("vehicle-1"))
            repeat(MAX_RETRYABLE_ATTEMPTS) {
                ambiguous.remote.pushResults += Outcome.Err(RemoteError.Unknown)
                ambiguous.controller.requestSync(SyncTrigger.PullToRefresh)
                advanceUntilIdle()
                ambiguous.clock.advanceBy(900_000)
            }
            assertEquals("FAILED_POISONED", ambiguous.persistence.states.getValue("vehicle-1"))
            assertEquals(listOf(SyncError.PayloadPoisoned), ambiguous.reportedErrors.map { it.first })

            // `PermissionDenied` and `InvalidArgument` still poison on the first attempt with their
            // own mapped SyncError.
            val denied = fixture().withOutbox(vehicleOutbox("vehicle-1"))
            denied.remote.pushResults += Outcome.Err(RemoteError.PermissionDenied)
            denied.controller.requestSync(SyncTrigger.AppForeground)
            advanceUntilIdle()
            assertEquals("FAILED_POISONED", denied.persistence.states.getValue("vehicle-1"))
            assertEquals(listOf(SyncError.PermissionDenied), denied.reportedErrors.map { it.first })

            val invalid = fixture().withOutbox(vehicleOutbox("vehicle-1"))
            invalid.remote.pushResults += Outcome.Err(RemoteError.InvalidArgument)
            invalid.controller.requestSync(SyncTrigger.AppForeground)
            advanceUntilIdle()
            assertEquals("FAILED_POISONED", invalid.persistence.states.getValue("vehicle-1"))
            assertEquals(listOf(SyncError.ValidationRejected), invalid.reportedErrors.map { it.first })
        }

    @Test
    fun poisonTransitionsAreReportedButConnectivityFailuresAreNot() =
        runTest {
            val poisoned = fixture().withOutbox(vehicleOutbox("poisoned"))
            poisoned.remote.pushResults += Outcome.Err(RemoteError.InvalidArgument)

            poisoned.controller.requestSync(SyncTrigger.AppForeground)
            advanceUntilIdle()

            assertEquals(listOf(SyncError.ValidationRejected), poisoned.reportedErrors.map { it.first })
            assertEquals(
                setOf("entityType", "code", "cycleId"),
                poisoned.reportedErrors
                    .single()
                    .second.keys,
            )

            val connectivity = fixture().withOutbox(vehicleOutbox("connectivity"))
            connectivity.remote.pushResults += Outcome.Err(RemoteError.Unavailable)
            connectivity.controller.requestSync(SyncTrigger.AppForeground)
            advanceUntilIdle()

            assertEquals(emptyList(), connectivity.reportedErrors)
        }

    @Test
    fun moreThanOnePageSharingATimestampCompletes() =
        runTest {
            val fixture = fixture()
            fixture.remote.seedDocuments(
                EntityType.VEHICLE,
                (1..401).map { index -> remoteVehicle("vehicle-${index.toString().padStart(3, '0')}", 1_000) },
            )

            fixture.controller.requestSync(SyncTrigger.AppForeground)
            advanceUntilIdle()

            assertEquals(401, fixture.persistence.vehicleIds.size)
            // 401 documents at one exact millisecond paginate 200 + 200 + 1; a full-precision cursor
            // makes each later `startAfter` exclusive, so no document is re-delivered.
            assertEquals(3, fixture.remote.vehiclePullCursors.size)
        }

    @Test
    fun aChangeSetOfExactlyOnePageCompletesWithoutAFalseProgressFailure() =
        runTest {
            // Exactly PULL_PAGE_LIMIT documents, all sharing one millisecond with a sub-millisecond
            // remainder. A millisecond-truncated cursor makes page 2 re-deliver the last document,
            // whose nextCursor equals the request cursor: the progress invariant would then fail the
            // cycle with ConflictUnresolved even though nothing is stranded (`§9.4`).
            val fixture = fixture()
            val sharedMillis = 5_000L
            val documents =
                (1..PULL_PAGE_LIMIT).map { index ->
                    remoteVehicleAtMicros(
                        id = "vehicle-${index.toString().padStart(3, '0')}",
                        epochMicros = sharedMillis * MICROS_PER_MILLISECOND + index,
                    )
                }
            fixture.remote.seedDocuments(EntityType.VEHICLE, documents)

            val result = fixture.controller.sync(SyncTrigger.AppForeground)

            assertEquals(Outcome.Ok(Unit), result)
            assertEquals(PULL_PAGE_LIMIT, fixture.persistence.vehicleIds.size)
            assertEquals(
                emptyList(),
                fixture.reportedErrors.map { it.first },
                "a millisecond-distinguishable change set must not report a progress failure",
            )
            assertFalse(fixture.controller.status.value is SyncStatus.Failed)
            assertEquals(
                instantFromEpochMicroseconds(sharedMillis * MICROS_PER_MILLISECOND + PULL_PAGE_LIMIT),
                fixture.persistence.cursors
                    .getValue(EntityType.VEHICLE)
                    .lastServerUpdatedAt,
                "the cursor must advance to the full-precision last document",
            )
        }

    @Test
    fun twoSameMillisecondDocumentsStraddlingAPageBoundaryDoNotFailTheCycle() =
        runTest {
            // Two documents inside the same millisecond straddle the boundary: the last document of
            // page 1 has a sub-millisecond remainder, so a truncated cursor re-delivers it and page 2
            // does not strictly advance. With a full-precision cursor page 2 is exclusive and the
            // cycle completes.
            val fixture = fixture()
            val sharedMillis = 7_000L
            val documents =
                (1..PULL_PAGE_LIMIT + 1).map { index ->
                    remoteVehicleAtMicros(
                        id = "vehicle-${index.toString().padStart(3, '0')}",
                        epochMicros = sharedMillis * MICROS_PER_MILLISECOND + index,
                    )
                }
            fixture.remote.seedDocuments(EntityType.VEHICLE, documents)

            val result = fixture.controller.sync(SyncTrigger.AppForeground)

            assertEquals(Outcome.Ok(Unit), result)
            assertEquals(PULL_PAGE_LIMIT + 1, fixture.persistence.vehicleIds.size)
            assertEquals(emptyList(), fixture.reportedErrors.map { it.first })
            assertFalse(fixture.controller.status.value is SyncStatus.Failed)
        }

    @Test
    fun nonAdvancingMillisecondPageFailsClosedWithoutApplyingData() =
        runTest {
            val fixture = fixture()
            fixture.remote.pullHandler = { type, cursor ->
                if (type == EntityType.VEHICLE) {
                    RemotePage(
                        items = listOf(remoteVehicle("vehicle-1", cursor.lastServerUpdatedAt.toEpochMilliseconds())),
                        nextCursor = cursor,
                        hasMore = true,
                    )
                } else {
                    page()
                }
            }

            fixture.controller.requestSync(SyncTrigger.AppForeground)
            advanceUntilIdle()

            assertIs<SyncStatus.Failed>(fixture.controller.status.value)
            assertFalse(fixture.persistence.vehicleIds.contains("vehicle-1"))
            assertFalse(fixture.persistence.cursors.containsKey(EntityType.VEHICLE))
        }

    @Test
    fun orphanFuelEntryBecomesVisibleWhenItsVehicleArrives() =
        runTest {
            val fixture = fixture()
            var vehicleAvailable = false
            fixture.remote.pullHandler = { type, _ ->
                when (type) {
                    EntityType.VEHICLE -> if (vehicleAvailable) page(remoteVehicle("vehicle-1", 2_000)) else page()
                    EntityType.FUEL_ENTRY -> page(remoteFuelEntry("entry-1", "vehicle-1", 1_000))
                }
            }
            fixture.controller.requestSync(SyncTrigger.AppForeground)
            advanceUntilIdle()
            assertTrue(fixture.persistence.fuelEntryIds.contains("entry-1"))
            assertFalse(fixture.persistence.visibleFuelEntryIds.contains("entry-1"))

            vehicleAvailable = true
            fixture.controller.requestSync(SyncTrigger.PullToRefresh)
            advanceUntilIdle()
            assertTrue(fixture.persistence.visibleFuelEntryIds.contains("entry-1"))
        }

    @Test
    fun duplicateVehicleNamesBothRestore() =
        runTest {
            val fixture = fixture()
            fixture.remote.pullHandler = { type, _ ->
                if (type == EntityType.VEHICLE) {
                    page(
                        remoteVehicle("vehicle-1", 1_000, name = "Car"),
                        remoteVehicle("vehicle-2", 2_000, name = "Car"),
                    )
                } else {
                    page()
                }
            }

            fixture.controller.requestSync(SyncTrigger.AppForeground)
            advanceUntilIdle()

            assertEquals(setOf("vehicle-1", "vehicle-2"), fixture.persistence.vehicleIds)
        }

    @Test
    fun localOwnerAdoptionRunsBeforePushAndIsIdempotent() =
        runTest {
            lateinit var fixture: Fixture
            var calls = 0
            var adopted = false
            fixture =
                fixture(adoption = {
                    calls += 1
                    if (!adopted) {
                        adopted = true
                        fixture.withOutbox(vehicleOutbox("vehicle-1"))
                    }
                    Outcome.Ok(Unit)
                })

            fixture.controller.requestSync(SyncTrigger.AppForeground)
            advanceUntilIdle()
            fixture.controller.requestSync(SyncTrigger.PullToRefresh)
            advanceUntilIdle()

            assertEquals(2, calls)
            assertEquals(1, fixture.remote.pushCalls.size)
        }

    @Test
    fun failedAdoptionRetriesAutomaticallyBeforeAnyRemoteWork() =
        runTest {
            var attempts = 0
            val fixture =
                fixture(
                    adoption = {
                        attempts += 1
                        if (attempts == 1) Outcome.Err(PersistenceError.TransactionFailed) else Outcome.Ok(Unit)
                    },
                ).withOutbox(vehicleOutbox("vehicle-1"))

            fixture.controller.requestSync(SyncTrigger.AppForeground)
            runCurrent()
            assertIs<SyncStatus.Failed>(fixture.controller.status.value)
            assertEquals(0, fixture.remote.pushCalls.size)

            advanceTimeBy(2_000)
            runCurrent()

            assertEquals(2, attempts)
            assertEquals(listOf("vehicle-1"), fixture.remote.pushCalls.map { it.entityId.value })
            assertEquals(SyncStatus.Idle, fixture.controller.status.value)
        }

    @Test
    fun unsupportedSchemaIsQuarantinedAndCursorAdvances() =
        runTest {
            val fixture = fixture()
            fixture.remote.pullHandler = { type, _ ->
                if (type == EntityType.VEHICLE) page(remoteVehicle("vehicle-1", 1_000, schemaVersion = 2)) else page()
            }

            fixture.controller.requestSync(SyncTrigger.AppForeground)
            advanceUntilIdle()

            assertEquals(
                QuarantineReason.UnsupportedSchemaVersion,
                fixture.persistence.quarantine
                    .single()
                    .reason,
            )
            assertFalse(fixture.persistence.vehicleIds.contains("vehicle-1"))
            assertEquals(
                "vehicle-1",
                fixture.persistence.cursors
                    .getValue(EntityType.VEHICLE)
                    .lastDocumentId
                    ?.value,
            )
        }

    @Test
    fun malformedPayloadIsQuarantinedAndCursorAdvances() =
        runTest {
            val fixture = fixture()
            val malformed =
                RemoteDocument(
                    EntityType.VEHICLE,
                    EntityId("vehicle-1"),
                    instant(1_000),
                    "{\"schemaVersion\":1,\"id\":false}",
                )
            fixture.remote.pullHandler = { type, _ -> if (type == EntityType.VEHICLE) page(malformed) else page() }

            fixture.controller.requestSync(SyncTrigger.AppForeground)
            advanceUntilIdle()

            val quarantine = fixture.persistence.quarantine.single()
            assertEquals(QuarantineReason.MalformedPayload, quarantine.reason)
            assertEquals(malformed.rawJson, quarantine.rawJson)
            assertEquals(
                "vehicle-1",
                fixture.persistence.cursors
                    .getValue(EntityType.VEHICLE)
                    .lastDocumentId
                    ?.value,
            )
            fixture.controller.requestSync(SyncTrigger.PullToRefresh)
            advanceUntilIdle()
            assertEquals(listOf("vehicle-1"), fixture.reportedQuarantines.map { it.entityId.value })
        }

    @Test
    fun injectedJitterProducesDeterministicCappedBackoff() {
        val minimum = JitterSource { _, _ -> 0 }
        val maximum = JitterSource { _, _ -> 400 }

        assertEquals(1_000L, retryDelayMillis(0, minimum))
        assertEquals(1_200L, retryDelayMillis(0, maximum))
        assertEquals(900_000L, retryDelayMillis(30, maximum))
    }

    @Test
    fun longOfflinePeriodNeverPoisonsAndBacksUpOnRecovery() =
        runTest {
            val fixture = fixture(online = false).withOutbox(vehicleOutbox("vehicle-1", attemptCount = 10))
            // A connectivity failure leaves the row PENDING with its retry context in the outbox
            // (§7/§9.7); the seed reflects that corrected state rather than the superseded
            // FAILED_RETRYABLE one.
            fixture.persistence.states["vehicle-1"] = "PENDING"
            fixture.persistence.outbox[0] =
                fixture.persistence.outbox[0].copy(lastErrorCode = RemoteError.Unavailable.code)

            fixture.clock.advanceBy(7 * 24 * 60 * 60 * 1_000L)
            fixture.controller.requestSync(SyncTrigger.Periodic)
            advanceUntilIdle()
            assertEquals(SyncStatus.Pending(1), fixture.controller.status.value)
            assertEquals("PENDING", fixture.persistence.states.getValue("vehicle-1"))

            fixture.connectivity.set(true)
            fixture.controller.requestSync(SyncTrigger.ConnectivityRecovered)
            advanceUntilIdle()
            assertEquals(SyncStatus.Idle, fixture.controller.status.value)
        }

    @Test
    fun concurrentTriggersProduceOneActiveAndOneFollowUpCycle() =
        runTest {
            val fixture = fixture()
            val firstPushStarted = CompletableDeferred<Unit>()
            val releasePush = CompletableDeferred<Unit>()
            fixture.withOutbox(vehicleOutbox("vehicle-1"))
            fixture.remote.onPushSuspend = {
                firstPushStarted.complete(Unit)
                releasePush.await()
            }

            fixture.controller.requestSync(SyncTrigger.AppForeground)
            firstPushStarted.await()
            fixture.controller.requestSync(SyncTrigger.Periodic)
            fixture.controller.requestSync(SyncTrigger.PostWriteDebounce)
            releasePush.complete(Unit)
            advanceUntilIdle()

            assertEquals(2, fixture.remote.vehiclePullCursors.size)
            assertEquals(1, fixture.remote.maxConcurrentPushes)
        }

    @Test
    fun initialSentinelInstanceNeverReachesRemoteSource() =
        runTest {
            val fixture = fixture()
            fixture.controller.requestSync(SyncTrigger.AppForeground)
            advanceUntilIdle()

            assertTrue(fixture.remote.pullCursors.none { it === RemoteCursor.INITIAL })
        }

    @Test
    fun retryFailedPropagatesOnlyLocalTransactionFailure() =
        runTest {
            val fixture = fixture()
            fixture.persistence.resetResult = Outcome.Err(PersistenceError.TransactionFailed)

            val result = fixture.controller.retryFailed()

            assertEquals(Outcome.Err(PersistenceError.TransactionFailed), result)
        }

    @Test
    fun databaseDiagnosticsAreAvailableOnlyInDebugBuilds() =
        runTest {
            val release = fixture(debugEnabled = false, debugLines = { listOf("sensitive-local-state") })
            val debug = fixture(debugEnabled = true, debugLines = { listOf("redacted-sync-state") })

            assertEquals(emptyList(), release.controller.debugLines())
            assertEquals(listOf("redacted-sync-state"), debug.controller.debugLines())
        }

    @Test
    fun fixedSeedBackupSimulationConvergesAfterLostResponses() =
        runTest {
            val random = Random(3_303)
            val fixture = fixture(jitter = JitterSource { from, until -> random.nextInt(from, until) })
            val expectedIds = mutableSetOf<String>()

            repeat(50) { index ->
                val id = "vehicle-${index.toString().padStart(2, '0')}"
                expectedIds += id
                fixture.withOutbox(vehicleOutbox(id))
                if (random.nextBoolean()) {
                    fixture.remote.pushResults += Outcome.Err(RemoteError.Unknown)
                    fixture.remote.pushResults += ack(id)
                }
                fixture.controller.requestSync(SyncTrigger.PostWriteDebounce)
                advanceUntilIdle()
                if (fixture.persistence.outbox.any { it.entityId.value == id }) {
                    fixture.clock.advanceBy(900_000)
                    fixture.controller.requestSync(SyncTrigger.Periodic)
                    advanceUntilIdle()
                }
            }

            assertEquals(expectedIds, fixture.remote.remoteIds)
            assertEquals(emptyList(), fixture.persistence.outbox)
            assertEquals(SyncStatus.Idle, fixture.controller.status.value)
        }

    private companion object {
        val OWNER = OwnerId("owner-1")
    }
}

/**
 * The `E3-03` owner-review round's new behavioural cases: the awaitable cycle (`D-171`), the real
 * aggregate counts on failure, the connectivity row/aggregate agreement, the `localRevision` guard
 * on the failure path and the `NotFound` success path. Kept in its own class so detekt's
 * `LargeClass` bound stays meaningful.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultSyncControllerReviewRoundTest {
    @Test
    fun localEditDuringInflightPushRemainsPending() =
        runTest {
            val fixture = fixture(now = 900_000).withOutbox(vehicleOutbox("vehicle-1"))
            fixture.persistence.outbox[0] =
                fixture.persistence.outbox[0].copy(
                    attemptCount = 4,
                    nextAttemptAt = instant(900_000),
                    lastErrorCode = RemoteError.Unknown.code,
                )
            fixture.persistence.lastErrors["vehicle-1"] = RemoteError.Unknown.code
            fixture.persistence.cycleIds["vehicle-1"] = "cycle-stale"
            fixture.remote.onPush = { fixture.persistence.edit("vehicle-1") }

            fixture.controller.requestSync(SyncTrigger.AppForeground)
            advanceUntilIdle()

            assertEquals(
                2,
                fixture.persistence.outbox
                    .single()
                    .localRevision,
            )
            assertEquals("PENDING", fixture.persistence.states.getValue("vehicle-1"))
            assertEquals(
                0,
                fixture.persistence.outbox
                    .single()
                    .attemptCount,
            )
            assertEquals(
                instant(0),
                fixture.persistence.outbox
                    .single()
                    .nextAttemptAt,
            )
            assertNull(
                fixture.persistence.outbox
                    .single()
                    .lastErrorCode,
            )
            assertNull(fixture.persistence.lastErrors["vehicle-1"])
            assertNull(fixture.persistence.cycleIds["vehicle-1"])
            // The editor sets PENDING in the same transaction as the local edit (§7 invariant,
            // §9.3), and the ack with a mismatched revision leaves the state unchanged. The real
            // sequence is therefore SYNCING -> PENDING, not SYNCING -> SYNCING.
            assertEquals(
                listOf("SYNCING", "PENDING"),
                fixture.persistence.stateHistory
                    .filter { it.first == "vehicle-1" }
                    .map { it.second },
            )
        }

    @Test
    fun automaticDueRetryTransitionsDirectlyFromFailedRetryableToSyncing() =
        runTest {
            val fixture = fixture().withOutbox(vehicleOutbox("vehicle-1"))
            fixture.remote.pushResults += Outcome.Err(RemoteError.Unknown)
            fixture.controller.requestSync(SyncTrigger.AppForeground)
            advanceUntilIdle()

            fixture.clock.advanceBy(900_000)
            fixture.remote.pushResults += ack("vehicle-1")
            fixture.controller.requestSync(SyncTrigger.Periodic)
            advanceUntilIdle()

            assertEquals(
                listOf("SYNCING", "FAILED_RETRYABLE", "SYNCING", "SYNCED"),
                fixture.persistence.stateHistory
                    .filter { it.first == "vehicle-1" }
                    .map { it.second },
            )
        }

    @Test
    fun retryCeilingPoisonIsStampedFromSyncing() =
        runTest {
            val fixture = fixture().withOutbox(vehicleOutbox("vehicle-1"))
            fixture.persistence.outbox[0] =
                fixture.persistence.outbox[0].copy(attemptCount = MAX_RETRYABLE_ATTEMPTS - 1)
            fixture.remote.pushResults += Outcome.Err(RemoteError.Unknown)

            fixture.controller.requestSync(SyncTrigger.AppForeground)
            advanceUntilIdle()

            assertEquals(
                listOf("SYNCING", "FAILED_POISONED"),
                fixture.persistence.stateHistory
                    .filter { it.first == "vehicle-1" }
                    .map { it.second },
            )
        }

    @Test
    fun everyObservedTransitionBelongsToTheSectionSevenAllowedSet() =
        runTest {
            val fixture = fixture().withOutbox(vehicleOutbox("vehicle-1"))
            fixture.remote.pushResults += Outcome.Err(RemoteError.Unknown)
            fixture.controller.requestSync(SyncTrigger.AppForeground)
            advanceUntilIdle()
            fixture.clock.advanceBy(900_000)
            fixture.remote.pushResults += Outcome.Err(RemoteError.InvalidArgument)
            fixture.controller.requestSync(SyncTrigger.Periodic)
            advanceUntilIdle()

            val observedStates =
                listOf("PENDING") +
                    fixture.persistence.stateHistory
                        .filter { it.first == "vehicle-1" }
                        .map { it.second }
            val observedTransitions = observedStates.zipWithNext().toSet()
            val documentedAllowedTransitions =
                setOf(
                    "SYNCED" to "PENDING",
                    "PENDING" to "PENDING",
                    "PENDING" to "SYNCING",
                    "SYNCING" to "SYNCED",
                    "SYNCING" to "PENDING",
                    "SYNCING" to "FAILED_RETRYABLE",
                    "FAILED_RETRYABLE" to "PENDING",
                    "FAILED_RETRYABLE" to "SYNCING",
                    "SYNCING" to "FAILED_POISONED",
                    "FAILED_POISONED" to "PENDING",
                )

            assertTrue(
                observedTransitions.all { it in documentedAllowedTransitions },
                "observed transitions missing from docs/CONTRACTS.md §7: " +
                    (observedTransitions - documentedAllowedTransitions),
            )
        }

    @Test
    fun awaitableSyncResolvesAgainstTheFollowUpCycleWithoutStartingASecond() =
        runTest {
            val fixture = fixture()
            val firstPushStarted = CompletableDeferred<Unit>()
            val releasePush = CompletableDeferred<Unit>()
            fixture.withOutbox(vehicleOutbox("vehicle-1"))
            fixture.remote.onPushSuspend = {
                firstPushStarted.complete(Unit)
                releasePush.await()
            }

            fixture.controller.requestSync(SyncTrigger.AppForeground)
            firstPushStarted.await()

            var awaited: Outcome<Unit, AppError>? = null
            val awaiting = launch { awaited = fixture.controller.sync(SyncTrigger.PullToRefresh) }
            runCurrent()
            // The awaited request joins the single pending follow-up rather than starting a cycle.
            assertNull(awaited)

            releasePush.complete(Unit)
            awaiting.join()

            assertEquals(Outcome.Ok(Unit), awaited)
            assertEquals(2, fixture.remote.vehiclePullCursors.size)
            assertEquals(1, fixture.remote.maxConcurrentPushes)
        }

    @Test
    fun syncRefusedOfflineOrLocalOwnerIsOkWithoutError() =
        runTest {
            val offline = fixture(online = false).withOutbox(vehicleOutbox("vehicle-1"))
            assertEquals(Outcome.Ok(Unit), offline.controller.sync(SyncTrigger.PullToRefresh))
            assertEquals(0, offline.remote.pushCalls.size)

            val localOwner =
                fixture(owner = LOCAL_OWNER).withOutbox(vehicleOutbox("vehicle-1"))
            assertEquals(Outcome.Ok(Unit), localOwner.controller.sync(SyncTrigger.PullToRefresh))
            assertEquals(0, localOwner.remote.pushCalls.size)
        }

    @Test
    fun syncCarriesTheUnderlyingPullFailure() =
        runTest {
            val fixture = fixture()
            fixture.remote.pullHandler = { _, _ -> error("pull failed") }

            val result = fixture.controller.sync(SyncTrigger.PullToRefresh)

            assertIs<Outcome.Err<AppError>>(result)
        }

    @Test
    fun unexpectedFailurePreservesRealPoisonedCount() =
        runTest {
            val fixture = fixture().withOutbox(vehicleOutbox("vehicle-1"))
            fixture.persistence.states["vehicle-1"] = "FAILED_POISONED"
            fixture.remote.pullHandler = { _, _ -> error("boom") }

            fixture.controller.requestSync(SyncTrigger.AppForeground)
            advanceUntilIdle()

            val status = assertIs<SyncStatus.Failed>(fixture.controller.status.value)
            assertEquals(1, status.poisonedCount)
        }

    @Test
    fun adoptionFailurePreservesRealPoisonedCount() =
        runTest {
            var failing = true
            val fixture =
                fixture(
                    adoption = { if (failing) Outcome.Err(PersistenceError.TransactionFailed) else Outcome.Ok(Unit) },
                ).withOutbox(vehicleOutbox("vehicle-1"))
            fixture.persistence.states["vehicle-1"] = "FAILED_POISONED"

            fixture.controller.requestSync(SyncTrigger.AppForeground)
            runCurrent()

            val status = assertIs<SyncStatus.Failed>(fixture.controller.status.value)
            assertEquals(1, status.poisonedCount)
            // The real retryable count is zero, so the synthetic 1 keeps the failure representable.
            assertEquals(1, status.retryableCount)

            // Let the scheduled adoption retry succeed so the test leaves no live job behind.
            failing = false
            advanceTimeBy(2_000)
            runCurrent()
            advanceUntilIdle()
        }

    @Test
    fun connectivityFailureKeepsRowStateAndAggregateInAgreement() =
        runTest {
            val fixture = fixture().withOutbox(vehicleOutbox("vehicle-1"))
            fixture.remote.pushResults += Outcome.Err(RemoteError.Unavailable)

            fixture.controller.requestSync(SyncTrigger.AppForeground)
            advanceUntilIdle()

            // A connectivity-only failure is a deferred retry, so the row and the aggregate agree.
            assertEquals("PENDING", fixture.persistence.states.getValue("vehicle-1"))
            assertEquals(SyncStatus.Pending(1), fixture.controller.status.value)
            // The retry context still lives in the outbox so the backoff is preserved.
            val row = fixture.persistence.outbox.single()
            assertEquals(1, row.attemptCount)
            assertEquals(RemoteError.Unavailable.code, row.lastErrorCode)
        }

    @Test
    fun localEditDuringFailingPushKeepsTheNewRevisionUnstamped() =
        runTest {
            val fixture = fixture().withOutbox(vehicleOutbox("vehicle-1"))
            fixture.remote.onPush = { fixture.persistence.edit("vehicle-1") }
            fixture.remote.pushResults += Outcome.Err(RemoteError.Unknown)

            fixture.controller.requestSync(SyncTrigger.AppForeground)
            advanceUntilIdle()

            val row = fixture.persistence.outbox.single()
            assertEquals(2, row.localRevision)
            assertEquals(0, row.attemptCount)
            assertNull(row.lastErrorCode)
            // The editor already moved the entity to PENDING; the stale failure leaves it there.
            assertEquals("PENDING", fixture.persistence.states.getValue("vehicle-1"))
        }

    @Test
    fun notFoundOnPushMarksSyncedWithNoServerTimestamp() =
        runTest {
            val fixture = fixture().withOutbox(vehicleOutbox("vehicle-1"))
            fixture.remote.pushResults += Outcome.Err(RemoteError.NotFound)

            fixture.controller.requestSync(SyncTrigger.AppForeground)
            advanceUntilIdle()

            assertEquals("SYNCED", fixture.persistence.states.getValue("vehicle-1"))
            assertNull(fixture.persistence.vehicleServerTimes["vehicle-1"])
            assertEquals(emptyList(), fixture.persistence.outbox)
        }

    @Test
    fun missingTopLevelKeysAreQuarantinedAndNeverEscapeTheCycle() =
        runTest {
            // Every top-level key read before the entity-specific validation must be total: a missing
            // key becomes a MalformedPayload quarantine record, not an escaping NoSuchElementException
            // that is swallowed by the generic cycle catch (`§9.5`, ADR-0171).
            listOf("id", "ownerId", "updatedAt", "deleted", "deletedAt").forEach { missing ->
                val fixture = fixture()
                val document =
                    RemoteDocument(
                        EntityType.VEHICLE,
                        EntityId("vehicle-1"),
                        instant(1_000),
                        vehicleRemoteJsonWithout(missing),
                    )
                fixture.remote.pullHandler = { type, _ ->
                    if (type == EntityType.VEHICLE) page(document) else page()
                }

                fixture.controller.requestSync(SyncTrigger.AppForeground)
                advanceUntilIdle()

                assertEquals(
                    listOf(QuarantineReason.MalformedPayload),
                    fixture.persistence.quarantine.map { it.reason },
                    "missing '$missing' must be quarantined as MalformedPayload",
                )
                assertEquals(
                    emptyList(),
                    fixture.reportedErrors.map { it.first },
                    "missing '$missing' must not be reported as an unexpected failure",
                )
                assertEquals(
                    SyncStatus.Idle,
                    fixture.controller.status.value,
                    "missing '$missing' must not produce a Failed status",
                )
                assertEquals(
                    "vehicle-1",
                    fixture.persistence.cursors[EntityType.VEHICLE]
                        ?.lastDocumentId
                        ?.value,
                    "missing '$missing' must still advance the cursor",
                )
            }
        }

    @Test
    fun missingFuelEntryTopLevelKeysAreQuarantinedAndNeverEscapeTheCycle() =
        runTest {
            listOf("id", "ownerId", "updatedAt", "deleted", "deletedAt").forEach { missing ->
                val fixture = fixture()
                val document =
                    RemoteDocument(
                        EntityType.FUEL_ENTRY,
                        EntityId("entry-1"),
                        instant(1_000),
                        fuelEntryRemoteJsonWithout(missing),
                    )
                fixture.remote.pullHandler = { type, _ ->
                    if (type == EntityType.FUEL_ENTRY) page(document) else page()
                }

                fixture.controller.requestSync(SyncTrigger.AppForeground)
                advanceUntilIdle()

                assertEquals(
                    listOf(QuarantineReason.MalformedPayload),
                    fixture.persistence.quarantine.map { it.reason },
                    "missing fuel-entry '$missing' must be quarantined as MalformedPayload",
                )
                assertEquals(
                    emptyList(),
                    fixture.reportedErrors.map { it.first },
                    "missing fuel-entry '$missing' must not be reported as an unexpected failure",
                )
                assertEquals(
                    SyncStatus.Idle,
                    fixture.controller.status.value,
                    "missing fuel-entry '$missing' must not produce a Failed status",
                )
                assertEquals(
                    "entry-1",
                    fixture.persistence.cursors[EntityType.FUEL_ENTRY]
                        ?.lastDocumentId
                        ?.value,
                    "missing fuel-entry '$missing' must still advance the cursor",
                )
            }
        }

    @Test
    fun poisonedRowIsNotRetriedAutomaticallyAndReportsOnce() =
        runTest {
            val fixture = fixture().withOutbox(vehicleOutbox("vehicle-1"))
            fixture.remote.pushResults += Outcome.Err(RemoteError.PermissionDenied)

            fixture.controller.requestSync(SyncTrigger.AppForeground)
            advanceUntilIdle()

            assertEquals("FAILED_POISONED", fixture.persistence.states.getValue("vehicle-1"))
            assertEquals(1, fixture.reportedErrors.size)
            val firstStatus = assertIs<SyncStatus.Failed>(fixture.controller.status.value)
            assertEquals(1, firstStatus.poisonedCount)

            // Past every backoff the poisoned row still must not be selected for another push.
            fixture.clock.advanceBy(900_000)
            fixture.controller.requestSync(SyncTrigger.Periodic)
            advanceUntilIdle()

            assertEquals(1, fixture.remote.pushCalls.size)
            assertEquals(1, fixture.reportedErrors.size)
            assertEquals("FAILED_POISONED", fixture.persistence.states.getValue("vehicle-1"))
            // The poisoned count survives the later cycle: the row never leaves FAILED_POISONED.
            val secondStatus = assertIs<SyncStatus.Failed>(fixture.controller.status.value)
            assertEquals(1, secondStatus.poisonedCount)
        }

    @Test
    fun nullDocumentIdCursorFailsClosedRegardlessOfTimestamp() =
        runTest {
            val fixture = fixture()
            fixture.persistence.cursors[EntityType.VEHICLE] = cursor(5_000, "anchor")
            val later = remoteVehicle("vehicle-1", 9_000)
            fixture.remote.pullHandler = { type, _ ->
                if (type == EntityType.VEHICLE) {
                    // A later timestamp but a null document id: the progress invariant cannot hold.
                    RemotePage(
                        items = listOf(later),
                        nextCursor = RemoteCursor(instant(9_000), null),
                        hasMore = true,
                    )
                } else {
                    page()
                }
            }

            val result = fixture.controller.sync(SyncTrigger.PullToRefresh)

            assertEquals(Outcome.Err(SyncError.ConflictUnresolved), result)
            assertEquals(cursor(5_000, "anchor"), fixture.persistence.cursors[EntityType.VEHICLE])
        }

    @Test
    fun nonAdvancingCursorFailsClosedWithConflictUnresolved() =
        runTest {
            val fixture = fixture()
            fixture.persistence.cursors[EntityType.VEHICLE] = cursor(5_000, "anchor")
            val stalled = remoteVehicle("vehicle-1", 5_000)
            fixture.remote.pullHandler = { type, _ ->
                if (type == EntityType.VEHICLE) {
                    // A page whose nextCursor does not strictly advance past the anchor.
                    RemotePage(items = listOf(stalled), nextCursor = cursor(5_000, "anchor"), hasMore = true)
                } else {
                    page()
                }
            }

            val result = fixture.controller.sync(SyncTrigger.PullToRefresh)

            assertEquals(Outcome.Err(SyncError.ConflictUnresolved), result)
            assertEquals(
                cursor(5_000, "anchor"),
                fixture.persistence.cursors[EntityType.VEHICLE],
                "the stored cursor must not advance on a progress-invariant failure",
            )
            assertEquals(
                listOf(SyncError.ConflictUnresolved),
                fixture.reportedErrors.map { it.first },
                "the stranding condition must be reported through onPoisoned",
            )
        }

    @Test
    fun pushDependencyOrderHoldsAcrossBatchBoundaries() =
        runTest {
            // A vehicle tombstone whose outbox row predates its fuel-entry tombstones (a lower `seq`)
            // must still be pushed AFTER them (`§8`), even when the change set spans more than one
            // 50-row batch. Ordering only within a batch would push the vehicle tombstone first.
            val fixture = fixture()
            val vehicle = vehicleOutbox("vehicle-1", sequence = 1, deleted = true)
            val entries =
                (1..PUSH_BATCH_LIMIT).map { index ->
                    fuelEntryOutbox(
                        "entry-$index",
                        vehicleId = "vehicle-1",
                        sequence = (index + 1).toLong(),
                        deleted = true,
                    )
                }
            fixture.persistence.outbox += vehicle
            fixture.persistence.states[vehicle.entityId.value] = "PENDING"
            entries.forEach { entry ->
                fixture.persistence.outbox += entry
                fixture.persistence.states[entry.entityId.value] = "PENDING"
            }

            fixture.controller.requestSync(SyncTrigger.PostWriteDebounce)
            advanceUntilIdle()

            val pushed = fixture.remote.pushCalls.map { it.entityId.value }
            assertEquals(PUSH_BATCH_LIMIT + 1, pushed.size)
            assertEquals(
                "vehicle-1",
                pushed.last(),
                "the vehicle tombstone must be pushed after every fuel-entry tombstone (`§8`), even across batches",
            )
            assertTrue(pushed.dropLast(1).all { it.startsWith("entry-") })
        }

    @Test
    fun manualRetryDuringAnActiveCycleKeepsPublishingSyncing() =
        runTest {
            // A manual retry issued while a cycle is running must not replace §9.9's `Syncing` for the
            // rest of that cycle; the active cycle publishes the aggregate when it finishes.
            val fixture = fixture().withOutbox(vehicleOutbox("vehicle-1"))
            val firstPushStarted = CompletableDeferred<Unit>()
            val releasePush = CompletableDeferred<Unit>()
            fixture.remote.onPushSuspend = {
                firstPushStarted.complete(Unit)
                releasePush.await()
            }

            fixture.controller.requestSync(SyncTrigger.AppForeground)
            firstPushStarted.await()
            assertEquals(SyncStatus.Syncing, fixture.controller.status.value)

            assertIs<Outcome.Ok<Unit>>(fixture.controller.retryFailed())
            assertEquals(
                SyncStatus.Syncing,
                fixture.controller.status.value,
                "a manual retry must not clobber an active cycle's Syncing status",
            )

            releasePush.complete(Unit)
            advanceUntilIdle()
            assertEquals(SyncStatus.Idle, fixture.controller.status.value)
        }

    @Test
    fun fullPushBatchContinuesUntilEveryDueRowIsPushedWithoutAnExternalTrigger() =
        runTest {
            // More than one full batch. A single cycle must drain every due row rather than pushing
            // only PUSH_BATCH_LIMIT and waiting for the next external trigger (`§9.3`, P2).
            val fixture = fixture()
            val rowCount = PUSH_BATCH_LIMIT * 2 + 20
            repeat(rowCount) { index -> fixture.withOutbox(vehicleOutbox("vehicle-$index")) }

            fixture.controller.requestSync(SyncTrigger.PostWriteDebounce)
            advanceUntilIdle()

            assertEquals(rowCount, fixture.remote.pushCalls.size, "every due row must be pushed")
            assertEquals(emptyList(), fixture.persistence.outbox)
            assertEquals(SyncStatus.Idle, fixture.controller.status.value)
        }

    @Test
    fun fullFailingPushBatchTerminatesTheContinuation() =
        runTest {
            // A full batch that keeps failing must not spin: each failure reschedules the row out of
            // the due window, so the continuation attempts every row once and then stops.
            val fixture = fixture()
            repeat(PUSH_BATCH_LIMIT + 10) { index ->
                fixture.withOutbox(vehicleOutbox("vehicle-$index"))
                fixture.remote.pushResults += Outcome.Err(RemoteError.Unavailable)
            }

            fixture.controller.requestSync(SyncTrigger.PostWriteDebounce)
            advanceUntilIdle()

            assertEquals(
                PUSH_BATCH_LIMIT + 10,
                fixture.remote.pushCalls.size,
                "each row is attempted exactly once; a failing full batch must not loop",
            )
            assertEquals(PUSH_BATCH_LIMIT + 10, fixture.persistence.outbox.size)
        }

    @Test
    fun aFinishingCycleNeverOverwritesTheSyncingStatusOfANewerCycle() =
        runTest {
            // The finishing cycle assigns `cycleRunning = false` under the mutex and then publishes
            // its terminal status after releasing it. A trigger that starts a new cycle in that
            // window publishes `Syncing`, which the pending publish must not overwrite (§9.9).
            val fixture = fixture()
            val countsReached = CompletableDeferred<Unit>()
            val releaseCounts = CompletableDeferred<Unit>()
            // The finishing cycle's terminal `refreshStatus()` reads counts; hold it there.
            fixture.persistence.gateCounts = countsReached to releaseCounts

            fixture.controller.requestSync(SyncTrigger.AppForeground)
            countsReached.await()
            assertEquals(SyncStatus.Syncing, fixture.controller.status.value)

            // The finishing cycle has released the mutex (`cycleRunning = false`) but not yet
            // published. A new cycle starts and publishes `Syncing`.
            val secondPushStarted = CompletableDeferred<Unit>()
            val releaseSecondPush = CompletableDeferred<Unit>()
            fixture.withOutbox(vehicleOutbox("vehicle-2"))
            fixture.remote.onPushSuspend = {
                secondPushStarted.complete(Unit)
                releaseSecondPush.await()
            }
            fixture.controller.requestSync(SyncTrigger.Periodic)
            secondPushStarted.await()
            assertEquals(SyncStatus.Syncing, fixture.controller.status.value)

            // Let the finishing cycle publish; the newer cycle is still running.
            releaseCounts.complete(Unit)
            runCurrent()
            assertEquals(
                SyncStatus.Syncing,
                fixture.controller.status.value,
                "a finishing cycle must not overwrite the Syncing status of a newer cycle",
            )

            releaseSecondPush.complete(Unit)
            advanceUntilIdle()
        }

    @Test
    fun concurrentJoinersAllCompleteAgainstOneFollowUpWithoutWedging() =
        runTest {
            // The pending follow-up and its completion handle are one value, so a follow-up can never
            // be flagged without a handle. Every concurrent `sync()` that joins it MUST complete, and
            // exactly one follow-up cycle MUST run (`§9.1`).
            val fixture = fixture()
            val firstPushStarted = CompletableDeferred<Unit>()
            val releasePush = CompletableDeferred<Unit>()
            fixture.withOutbox(vehicleOutbox("vehicle-1"))
            fixture.remote.onPushSuspend = {
                firstPushStarted.complete(Unit)
                releasePush.await()
            }

            fixture.controller.requestSync(SyncTrigger.AppForeground)
            firstPushStarted.await()

            val joinerResults = mutableListOf<Outcome<Unit, AppError>>()
            val alternativeTriggers = listOf(SyncTrigger.Periodic, SyncTrigger.PullToRefresh)
            val joiners =
                List(5) { index ->
                    launch {
                        joinerResults += fixture.controller.sync(alternativeTriggers[index % 2])
                    }
                }
            runCurrent()

            releasePush.complete(Unit)
            joiners.forEach { it.join() }
            advanceUntilIdle()

            assertEquals(5, joinerResults.size, "every joining sync() must complete; none may wedge")
            assertTrue(joinerResults.all { it == Outcome.Ok(Unit) })
            // One active cycle plus exactly one follow-up: two pull passes, not six.
            assertEquals(2, fixture.remote.vehiclePullCursors.size)
            assertEquals(1, fixture.remote.maxConcurrentPushes)
        }

    @Test
    fun coalescedConnectivityRecoveredStillMarksFailuresDueInTheFollowUp() =
        runTest {
            val fixture = fixture().withOutbox(vehicleOutbox("vehicle-1"))
            val firstPushStarted = CompletableDeferred<Unit>()
            val releasePush = CompletableDeferred<Unit>()
            fixture.remote.onPushSuspend = {
                firstPushStarted.complete(Unit)
                releasePush.await()
            }

            // A cycle is in flight; connectivity returns and its trigger is coalesced into the
            // single pending follow-up. The follow-up MUST still run the reason-dependent step.
            fixture.controller.requestSync(SyncTrigger.AppForeground)
            firstPushStarted.await()
            fixture.controller.requestSync(SyncTrigger.ConnectivityRecovered)
            releasePush.complete(Unit)
            advanceUntilIdle()

            assertEquals(
                listOf(instant(0)),
                fixture.persistence.connectivityDueCalls,
                "the coalesced ConnectivityRecovered follow-up must mark connectivity failures due",
            )
        }

    @Test
    fun coalescedConnectivityRecoveredMakesTheFailedRowDueAndPreservesAttempts() =
        runTest {
            val fixture = fixture().withOutbox(vehicleOutbox("vehicle-2"))
            // `vehicle-1` failed for connectivity and is parked behind a far-future backoff, so it is
            // not due in the first cycle. `vehicle-2` keeps the first cycle alive until the
            // coalesced trigger arrives.
            fixture.withOutbox(vehicleOutbox("vehicle-1"))
            fixture.persistence.states["vehicle-1"] = "FAILED_RETRYABLE"
            fixture.persistence.outbox[1] =
                fixture.persistence.outbox[1].copy(
                    attemptCount = 4,
                    nextAttemptAt = instant(900_000),
                    lastErrorCode = RemoteError.Unavailable.code,
                )
            val firstPushStarted = CompletableDeferred<Unit>()
            val releasePush = CompletableDeferred<Unit>()
            fixture.remote.onPushSuspend = {
                firstPushStarted.complete(Unit)
                releasePush.await()
            }

            fixture.controller.requestSync(SyncTrigger.AppForeground)
            firstPushStarted.await()
            fixture.controller.requestSync(SyncTrigger.ConnectivityRecovered)
            releasePush.complete(Unit)
            advanceUntilIdle()

            // The follow-up made the failed row due and pushed it, preserving its attemptCount.
            assertTrue(
                fixture.remote.pushCalls
                    .map { it.entityId.value }
                    .contains("vehicle-1"),
            )
            assertEquals(4, fixture.persistence.dueAttemptCounts["vehicle-1"])
            assertEquals(null, fixture.persistence.outbox.firstOrNull { it.entityId.value == "vehicle-1" })
        }

    @Test
    fun coalescedNonConnectivityTriggerDoesNotMarkFailuresDue() =
        runTest {
            val fixture = fixture().withOutbox(vehicleOutbox("vehicle-1"))
            val firstPushStarted = CompletableDeferred<Unit>()
            val releasePush = CompletableDeferred<Unit>()
            fixture.remote.onPushSuspend = {
                firstPushStarted.complete(Unit)
                releasePush.await()
            }

            fixture.controller.requestSync(SyncTrigger.AppForeground)
            firstPushStarted.await()
            fixture.controller.requestSync(SyncTrigger.Periodic)
            releasePush.complete(Unit)
            advanceUntilIdle()

            assertEquals(
                emptyList(),
                fixture.persistence.connectivityDueCalls,
                "a coalesced non-connectivity trigger must not mark connectivity failures due",
            )
        }
}

/** A valid Fuel Entry remote payload with exactly one top-level key omitted. */
private fun fuelEntryRemoteJsonWithout(missing: String): String =
    buildJsonObject {
        if (missing != "id") put("id", "entry-1")
        if (missing != "ownerId") put("ownerId", "owner-1")
        put("vehicleId", "vehicle-1")
        put("date", 0)
        put("odometerKm", 1)
        put("litersScaled", 1)
        put("pricePerLiterScaled", 1)
        put("totalCostMinor", 1)
        put("currency", "EUR")
        put("isFullTank", true)
        put("hasMissedEntries", false)
        put("odometerInconsistent", false)
        put("notes", JsonNull)
        put("createdAt", 0)
        if (missing != "updatedAt") put("updatedAt", 1_000)
        if (missing != "deleted") put("deleted", false)
        if (missing != "deletedAt") put("deletedAt", JsonNull)
        put("schemaVersion", 1)
    }.toString()

/** A valid Vehicle remote payload with exactly one top-level key omitted. */
private fun vehicleRemoteJsonWithout(missing: String): String =
    buildJsonObject {
        if (missing != "id") put("id", "vehicle-1")
        if (missing != "ownerId") put("ownerId", "owner-1")
        put("name", "Roadster")
        put("initialOdometerKm", 0)
        put("brand", JsonNull)
        put("model", JsonNull)
        put("fuelType", "GASOLINE")
        put("createdAt", 0)
        if (missing != "updatedAt") put("updatedAt", 1_000)
        if (missing != "deleted") put("deleted", false)
        if (missing != "deletedAt") put("deletedAt", JsonNull)
        put("schemaVersion", 1)
    }.toString()

private fun TestScope.fixture(
    online: Boolean = true,
    now: Long = 0,
    owner: OwnerId = OwnerId("owner-1"),
    adoption: suspend () -> Outcome<Unit, AppError> = { Outcome.Ok(Unit) },
    jitter: JitterSource = JitterSource { _, _ -> 200 },
    debugEnabled: Boolean = false,
    debugLines: suspend () -> List<String> = { emptyList() },
): Fixture {
    val clock = TestClock(instant(now))
    val connectivity = TestConnectivity(online)
    val persistence = FakeSyncPersistence()
    val remote = FakeRemoteSyncSource()
    val reportedErrors = mutableListOf<Pair<AppError, Map<String, String>>>()
    val reportedQuarantines = mutableListOf<QuarantineRecord>()
    val controller =
        DefaultSyncController(
            scope = this,
            ownerContext = TestOwnerContext(owner),
            connectivity = connectivity,
            remote = remote,
            persistence = persistence,
            clock = clock,
            uuidGenerator = TestUuidGenerator(),
            jitter = jitter,
            adoption = adoption,
            onPoisoned = { error, fields -> reportedErrors += error to fields },
            onQuarantined = reportedQuarantines::add,
            debugEnabled = debugEnabled,
            debugLoader = debugLines,
        )
    return Fixture(controller, persistence, remote, connectivity, clock, reportedErrors, reportedQuarantines)
}

private data class Fixture(
    val controller: DefaultSyncController,
    val persistence: FakeSyncPersistence,
    val remote: FakeRemoteSyncSource,
    val connectivity: TestConnectivity,
    val clock: TestClock,
    val reportedErrors: List<Pair<AppError, Map<String, String>>>,
    val reportedQuarantines: List<QuarantineRecord>,
) {
    fun withOutbox(row: OutboxRecord): Fixture {
        persistence.outbox += row
        persistence.states[row.entityId.value] = "PENDING"
        return this
    }
}

/**
 * A remote source that models Firestore's ordering faithfully.
 *
 * Firestore orders by the stored `(updatedAt, __name__)` pair, where `updatedAt` is a server
 * timestamp at microsecond precision, and `startAfter(boundary, documentId)` is a strict total-order
 * comparison against it. This fake keeps the **stored** microsecond ordering key separate from the
 * `serverUpdatedAt` the integration *delivers* into `RemoteDocument`, because that separation is the
 * whole defect: a millisecond-truncated delivery makes the next page's boundary sort before a
 * document whose true timestamp has a sub-millisecond remainder, so it is re-delivered.
 *
 * [deliverUpdatedAtTruncatedToMillis] models the integration. It is `false` for the fixed behaviour
 * (full precision carried through) and `true` for the pre-fix behaviour. The fake NEVER models
 * `startAfter` as index-exclusive on the document id alone.
 */
private class FakeRemoteSyncSource : RemoteSyncSource {
    val pushCalls = mutableListOf<EntitySnapshot>()
    val pushResults = ArrayDeque<Outcome<RemoteAck, RemoteError>>()
    val remoteIds = mutableSetOf<String>()
    val pullCursors = mutableListOf<RemoteCursor>()
    val vehiclePullCursors = mutableListOf<RemoteCursor>()
    val pagedDocuments = mutableMapOf<EntityType, List<StoredDocument>>()
    var pullHandler: (EntityType, RemoteCursor) -> RemotePage = { _, cursor -> RemotePage(emptyList(), cursor, false) }
    var onPush: () -> Unit = {}
    var onPushSuspend: suspend () -> Unit = {}
    var activePushes = 0
    var maxConcurrentPushes = 0
    var deliverUpdatedAtTruncatedToMillis = false

    /**
     * Seeds the stored documents for one entity type. The document's `serverUpdatedAt` is its true
     * stored ordering timestamp, at whatever precision the test gives it; ordering and the
     * `startAfter` boundary use it directly. Delivery may still truncate it (see
     * [deliverUpdatedAtTruncatedToMillis]), which is what models the integration.
     */
    fun seedDocuments(
        entityType: EntityType,
        documents: List<RemoteDocument>,
    ) {
        pagedDocuments[entityType] =
            documents.map { document ->
                StoredDocument(
                    document = document,
                    storedUpdatedAtMicros = document.serverUpdatedAt.toEpochMicroseconds(),
                )
            }
    }

    override suspend fun pushSnapshot(
        ownerId: OwnerId,
        snapshot: EntitySnapshot,
    ): Outcome<RemoteAck, RemoteError> {
        pushCalls += snapshot
        activePushes += 1
        maxConcurrentPushes = maxOf(maxConcurrentPushes, activePushes)
        return try {
            onPushSuspend()
            onPush()
            val result = pushResults.removeFirstOrNull() ?: ack(snapshot.entityId.value)
            if (result is Outcome.Ok) remoteIds += snapshot.entityId.value
            result
        } finally {
            activePushes -= 1
        }
    }

    override suspend fun pullChanges(
        ownerId: OwnerId,
        entityType: EntityType,
        cursor: RemoteCursor,
        limit: Int,
    ): Outcome<RemotePage, RemoteError> {
        pullCursors += cursor
        if (entityType == EntityType.VEHICLE) vehiclePullCursors += cursor
        val documents = pagedDocuments[entityType]
        val page =
            if (documents == null) {
                pullHandler(entityType, cursor)
            } else {
                val ordered =
                    documents.sortedWith(compareBy({ it.storedUpdatedAtMicros }, { it.document.documentId.value }))
                val boundaryId = cursor.lastDocumentId?.value
                val boundaryMicros = cursor.lastServerUpdatedAt.toEpochMicroseconds()
                val remaining =
                    if (boundaryId == null) {
                        ordered
                    } else {
                        // Strict total order: keep only documents sorting strictly after
                        // `(boundary, documentId)`.
                        ordered.dropWhile { stored ->
                            stored.storedUpdatedAtMicros < boundaryMicros ||
                                (
                                    stored.storedUpdatedAtMicros == boundaryMicros &&
                                        stored.document.documentId.value <= boundaryId
                                )
                        }
                    }
                val items = remaining.take(limit).map(::deliver)
                RemotePage(
                    items = items,
                    nextCursor = items.lastOrNull()?.let { RemoteCursor(it.serverUpdatedAt, it.documentId) } ?: cursor,
                    hasMore = items.size == limit,
                )
            }
        return Outcome.Ok(if (page.items.isEmpty()) page.copy(nextCursor = cursor) else page)
    }

    /** The integration boundary: the ordering timestamp delivered into `RemoteDocument`. */
    private fun deliver(stored: StoredDocument): RemoteDocument =
        if (deliverUpdatedAtTruncatedToMillis) {
            stored.document.copy(serverUpdatedAt = instant(stored.document.serverUpdatedAt.toEpochMilliseconds()))
        } else {
            stored.document
        }
}

private data class StoredDocument(
    val document: RemoteDocument,
    val storedUpdatedAtMicros: Long,
)

private class FakeSyncPersistence : SyncPersistence {
    val outbox = mutableListOf<OutboxRecord>()
    val states = mutableMapOf<String, String>()
    val stateHistory = mutableListOf<Pair<String, String>>()
    val lastErrors = mutableMapOf<String, String>()
    val cycleIds = mutableMapOf<String, String>()
    val calls = mutableListOf<String>()
    val cursors = mutableMapOf<EntityType, RemoteCursor>()
    val vehicleServerTimes = mutableMapOf<String, Long?>()
    val vehicleIds = mutableSetOf<String>()
    val fuelEntryIds = mutableSetOf<String>()
    val fuelEntryVehicles = mutableMapOf<String, String>()
    val deletedVehicles = mutableSetOf<String>()
    val quarantine = mutableListOf<QuarantineRecord>()
    var resetResult: Outcome<Unit, AppError> = Outcome.Ok(Unit)

    /** Every `markConnectivityFailuresDue` call, so a reason-dependent step is observable. */
    val connectivityDueCalls = mutableListOf<Instant>()

    /** The `attemptCount` each entity held when it was selected as due, for preservation checks. */
    val dueAttemptCounts = mutableMapOf<String, Int>()

    val visibleFuelEntryIds: Set<String>
        get() = fuelEntryIds.filterTo(mutableSetOf()) { fuelEntryVehicles[it] in vehicleIds }

    override suspend fun isOwnerDatabaseEmpty(ownerId: OwnerId): Boolean =
        (vehicleIds.isEmpty() && fuelEntryIds.isEmpty() && outbox.isEmpty()).also { calls += "isEmpty" }

    override suspend fun dueOutbox(
        now: Instant,
        limit: Int,
    ): List<OutboxRecord> {
        calls += "dueOutbox"
        // Mirror `selectDueOutbox` (`§8`, `§9.3`): the global dependency-group order is applied by the
        // selection, before the batch limit, so a vehicle tombstone cannot overtake fuel-entry
        // tombstones across a batch boundary.
        val due =
            outbox
                .filter { it.nextAttemptAt <= now && states[it.entityId.value] != "FAILED_POISONED" }
                .sortedWith(compareBy({ it.dependencyGroupForTest() }, { it.sequence }))
                .take(limit)
        due.forEach { dueAttemptCounts[it.entityId.value] = it.attemptCount }
        return due
    }

    private fun OutboxRecord.dependencyGroupForTest(): Int =
        when {
            entityType == EntityType.VEHICLE && !deleted -> 0
            entityType == EntityType.FUEL_ENTRY && !deleted -> 1
            entityType == EntityType.FUEL_ENTRY -> 2
            else -> 3
        }

    override suspend fun markSyncing(row: OutboxRecord) {
        // Mirror `SyncDatabaseAccess.markSyncing`: a poisoned row is never moved to SYNCING, so it
        // stays FAILED_POISONED and keeps counting towards the aggregate poison count (`§7`).
        if (states[row.entityId.value] == "FAILED_POISONED") return
        transition(row.entityId.value, "SYNCING")
    }

    override suspend fun confirmPush(
        row: OutboxRecord,
        serverUpdatedAt: Instant?,
    ) {
        val current = outbox.firstOrNull { it.entityId == row.entityId } ?: return
        // Mirror the production SQL (`confirmVehiclePush`/`confirmFuelEntryPush`): only an unchanged
        // revision becomes SYNCED; a mismatched one keeps the entity state, which the editor already
        // set to PENDING, and the ack only stamps `serverUpdatedAt` (§9.3).
        if (current.localRevision == row.localRevision) {
            outbox.remove(current)
            lastErrors.remove(row.entityId.value)
            cycleIds.remove(row.entityId.value)
            transition(row.entityId.value, "SYNCED")
        }
        vehicleServerTimes[row.entityId.value] = serverUpdatedAt?.toEpochMilliseconds()
    }

    override suspend fun failPush(
        row: OutboxRecord,
        attemptCount: Int,
        nextAttemptAt: Instant,
        errorCode: String,
        poisoned: Boolean,
        cycleId: CycleId,
    ) {
        val index = outbox.indexOfFirst { it.entityId == row.entityId }
        if (index < 0) return
        // The failure path is guarded by `localRevision` exactly like the confirm path: a local edit
        // that landed during the in-flight push must not inherit this attempt's outcome.
        if (outbox[index].localRevision != row.localRevision) return
        outbox[index] =
            outbox[index].copy(attemptCount = attemptCount, nextAttemptAt = nextAttemptAt, lastErrorCode = errorCode)
        lastErrors[row.entityId.value] = errorCode
        cycleIds[row.entityId.value] = cycleId.value
        // Mirror `SyncDatabaseAccess`: a connectivity-only failure is a deferred retry and leaves the
        // row PENDING, so the row state agrees with the aggregate `SyncStatus` (`§9.9`).
        val state =
            when {
                poisoned -> "FAILED_POISONED"
                errorCode in CONNECTIVITY_ERROR_CODES -> "PENDING"
                else -> "FAILED_RETRYABLE"
            }
        transition(row.entityId.value, state)
    }

    override suspend fun cursor(entityType: EntityType): RemoteCursor {
        calls += "cursor"
        return cursors[entityType] ?: RemoteCursor.INITIAL
    }

    override suspend fun applyPullPage(
        ownerId: OwnerId,
        entityType: EntityType,
        records: List<PullRecord>,
        cursor: RemoteCursor,
    ): List<QuarantineRecord> {
        val existing = quarantine.mapTo(mutableSetOf()) { it.entityType to it.entityId }
        records.forEach(::applyPullRecord)
        cursors[entityType] = cursor
        return records.filterIsInstance<PullRecord.Quarantined>().map { it.record }.filter {
            (it.entityType to it.entityId) !in existing
        }
    }

    private fun applyPullRecord(record: PullRecord) {
        when (record) {
            is PullRecord.Quarantined -> quarantine += record.record
            is PullRecord.Vehicle -> applyVehicle(record)
            is PullRecord.FuelEntry -> applyFuelEntry(record)
        }
    }

    private fun applyVehicle(record: PullRecord.Vehicle) {
        val id = record.document.documentId.value
        val remoteTime = record.document.serverUpdatedAt.toEpochMilliseconds()
        if (outbox.any { it.entityId.value == id } || vehicleServerTimes[id]?.let { remoteTime <= it } == true) return
        vehicleIds += id
        vehicleServerTimes[id] = remoteTime
        if (record.deletedAt == null) deletedVehicles -= id else deletedVehicles += id
    }

    private fun applyFuelEntry(record: PullRecord.FuelEntry) {
        val id = record.document.documentId.value
        if (outbox.any { it.entityId.value == id }) return
        fuelEntryIds += id
        fuelEntryVehicles[id] = record.vehicleId.value
    }

    override suspend fun markConnectivityFailuresDue(now: Instant) {
        connectivityDueCalls += now
        outbox.indices.forEach { index ->
            val row = outbox[index]
            if (row.lastErrorCode in setOf(RemoteError.Unavailable.code, RemoteError.DeadlineExceeded.code)) {
                outbox[index] = row.copy(nextAttemptAt = now)
            }
        }
    }

    override suspend fun resetFailed(now: Instant): Outcome<Unit, AppError> {
        if (resetResult is Outcome.Err) return resetResult
        outbox.indices.forEach { index ->
            val row = outbox[index]
            if (states[row.entityId.value] in setOf("FAILED_RETRYABLE", "FAILED_POISONED")) {
                outbox[index] = row.copy(attemptCount = 0, nextAttemptAt = now, lastErrorCode = null)
                lastErrors.remove(row.entityId.value)
                cycleIds.remove(row.entityId.value)
                transition(row.entityId.value, "PENDING")
            }
        }
        return Outcome.Ok(Unit)
    }

    /**
     * A one-shot gate on the next `counts()` read, used to hold a finishing cycle inside its terminal
     * status publication and observe what a cycle that starts in that window publishes.
     */
    var gateCounts: Pair<CompletableDeferred<Unit>, CompletableDeferred<Unit>>? = null

    override suspend fun counts(): SyncCounts {
        gateCounts?.let { (reached, release) ->
            gateCounts = null
            reached.complete(Unit)
            release.await()
        }
        var pending = 0
        var retryable = 0
        var poisoned = 0
        outbox.forEach { row ->
            when (states[row.entityId.value]) {
                "FAILED_POISONED" -> {
                    poisoned += 1
                }

                "FAILED_RETRYABLE" -> {
                    if (row.lastErrorCode in
                        setOf(RemoteError.Unavailable.code, RemoteError.DeadlineExceeded.code)
                    ) {
                        pending += 1
                    } else {
                        retryable +=
                            1
                    }
                }

                else -> {
                    pending += 1
                }
            }
        }
        return SyncCounts(pending, retryable, poisoned)
    }

    fun edit(entityId: String) {
        val index = outbox.indexOfFirst { it.entityId.value == entityId }
        outbox[index] =
            outbox[index].copy(
                localRevision = outbox[index].localRevision + 1,
                attemptCount = 0,
                nextAttemptAt = instant(0),
                lastErrorCode = null,
            )
        lastErrors.remove(entityId)
        cycleIds.remove(entityId)
        // Mirror the production editor (`updateVehicleRow`/`updateFuelEntryRow`), which sets the
        // entity to PENDING and coalesces a clean retry context in the same transaction as the edit
        // (`coalesceOutbox`, §7 invariant, §9.3).
        transition(entityId, "PENDING")
    }

    private fun transition(
        entityId: String,
        state: String,
    ) {
        states[entityId] = state
        stateHistory += entityId to state
    }
}

private fun vehicleOutbox(
    id: String,
    attemptCount: Int = 0,
    sequence: Long = 1,
    deleted: Boolean = false,
): OutboxRecord =
    OutboxRecord(
        sequence,
        EntityType.VEHICLE,
        EntityId(id),
        vehicleJson(id, 0, deleted = deleted),
        1,
        attemptCount,
        instant(0),
        null,
        deleted,
    )

private fun fuelEntryOutbox(
    id: String,
    vehicleId: String,
    sequence: Long,
    deleted: Boolean = false,
): OutboxRecord =
    OutboxRecord(
        sequence,
        EntityType.FUEL_ENTRY,
        EntityId(id),
        fuelJson(id, vehicleId, 0),
        1,
        0,
        instant(0),
        null,
        deleted,
    )

private fun ack(id: String): Outcome<RemoteAck, RemoteError> =
    Outcome.Ok(RemoteAck(EntityType.VEHICLE, EntityId(id), instant(1_000)))

private fun cursor(
    epochMillis: Long,
    id: String,
): RemoteCursor = RemoteCursor(instant(epochMillis), EntityId(id))

private fun page(
    vararg items: RemoteDocument,
    hasMore: Boolean = false,
): RemotePage =
    RemotePage(
        items.toList(),
        items.lastOrNull()?.let { RemoteCursor(it.serverUpdatedAt, it.documentId) } ?: RemoteCursor(instant(0), null),
        hasMore,
    )

private fun remoteVehicle(
    id: String,
    serverUpdatedAt: Long,
    deleted: Boolean = false,
    name: String = id,
    schemaVersion: Int = 1,
): RemoteDocument =
    RemoteDocument(
        EntityType.VEHICLE,
        EntityId(id),
        instant(serverUpdatedAt),
        vehicleJson(id, serverUpdatedAt, deleted, name, schemaVersion),
    )

/** A vehicle whose ordering timestamp has a sub-millisecond component (`D-174`). */
private fun remoteVehicleAtMicros(
    id: String,
    epochMicros: Long,
): RemoteDocument {
    val millis = epochMicros / MICROS_PER_MILLISECOND
    return RemoteDocument(
        EntityType.VEHICLE,
        EntityId(id),
        instantFromEpochMicroseconds(epochMicros),
        vehicleJson(id, millis),
    )
}

private fun remoteFuelEntry(
    id: String,
    vehicleId: String,
    serverUpdatedAt: Long,
): RemoteDocument =
    RemoteDocument(
        EntityType.FUEL_ENTRY,
        EntityId(id),
        instant(serverUpdatedAt),
        fuelJson(id, vehicleId, serverUpdatedAt),
    )

private fun vehicleJson(
    id: String,
    updatedAt: Long,
    deleted: Boolean = false,
    name: String = id,
    schemaVersion: Int = 1,
): String =
    """
    {
      "id":"$id","ownerId":"owner-1","name":"$name","initialOdometerKm":0,
      "brand":null,"model":null,"fuelType":"GASOLINE","createdAt":0,"updatedAt":$updatedAt,
      "deleted":$deleted,"deletedAt":${if (deleted) updatedAt else "null"},"schemaVersion":$schemaVersion
    }
    """.trimIndent()

private fun fuelJson(
    id: String,
    vehicleId: String,
    updatedAt: Long,
): String =
    """
    {
      "id":"$id","ownerId":"owner-1","vehicleId":"$vehicleId","date":0,"odometerKm":1,
      "litersScaled":1,"pricePerLiterScaled":1,"totalCostMinor":1,"currency":"EUR",
      "isFullTank":true,"hasMissedEntries":false,"odometerInconsistent":false,"notes":null,
      "createdAt":0,"updatedAt":$updatedAt,"deleted":false,"deletedAt":null,"schemaVersion":1
    }
    """.trimIndent()

private fun instant(epochMillis: Long): Instant = Instant.fromEpochMilliseconds(epochMillis)

private class TestClock(
    initial: Instant,
) : AppClock {
    private var current = initial

    override fun now(): Instant = current

    fun advanceBy(millis: Long) {
        current = Instant.fromEpochMilliseconds(current.toEpochMilliseconds() + millis)
    }
}

private class TestConnectivity(
    initiallyOnline: Boolean,
) : ConnectivityObserver {
    private val mutable = MutableStateFlow(initiallyOnline)
    override val isOnline: StateFlow<Boolean> = mutable

    fun set(value: Boolean) {
        mutable.value = value
    }
}

private class TestOwnerContext(
    initial: OwnerId,
) : OwnerContext {
    private val mutable = MutableStateFlow(initial)
    override val current: OwnerId get() = mutable.value

    override fun observe(): Flow<OwnerId> = mutable
}

private class TestUuidGenerator : UuidGenerator {
    private var sequence = 0

    override fun newId(): String {
        sequence += 1
        return "00000000-0000-4000-8000-${sequence.toString().padStart(12, '0')}"
    }
}

private const val MICROS_PER_MILLISECOND = 1_000L
