package com.ruizurraca.carapp.core.sync

import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.AppClock
import com.ruizurraca.carapp.core.common.ConnectivityObserver
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.OwnerContext
import com.ruizurraca.carapp.core.common.PersistenceError
import com.ruizurraca.carapp.core.common.RemoteError
import com.ruizurraca.carapp.core.common.SyncStatus
import com.ruizurraca.carapp.core.common.SyncTrigger
import com.ruizurraca.carapp.core.common.UuidGenerator
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.OwnerId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
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
    fun localEditDuringInflightPushRemainsPending() =
        runTest {
            val fixture = fixture().withOutbox(vehicleOutbox("vehicle-1"))
            fixture.remote.onPush = { fixture.persistence.edit("vehicle-1") }

            fixture.controller.requestSync(SyncTrigger.AppForeground)
            advanceUntilIdle()

            assertEquals(2, fixture.persistence.outbox.single().localRevision)
            assertEquals("PENDING", fixture.persistence.states.getValue("vehicle-1"))
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

            assertEquals(30_000L, fixture.remote.vehiclePullCursors.first().lastServerUpdatedAt.toEpochMilliseconds())
            assertNull(fixture.remote.vehiclePullCursors.first().lastDocumentId)
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
            val documents = (1..1_000).map { index -> remoteVehicle("vehicle-${index.toString().padStart(4, '0')}", index.toLong()) }
            fixture.remote.pagedDocuments[EntityType.VEHICLE] = documents

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
            assertEquals(0, fixture.persistence.outbox.single().attemptCount)
            assertEquals("PENDING", fixture.persistence.states.getValue("vehicle-1"))
        }

    @Test
    fun moreThanOnePageSharingATimestampCompletes() =
        runTest {
            val fixture = fixture()
            fixture.remote.pagedDocuments[EntityType.VEHICLE] =
                (1..401).map { index -> remoteVehicle("vehicle-${index.toString().padStart(3, '0')}", 1_000) }

            fixture.controller.requestSync(SyncTrigger.AppForeground)
            advanceUntilIdle()

            assertEquals(401, fixture.persistence.vehicleIds.size)
            assertEquals(3, fixture.remote.vehiclePullCursors.size)
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
                    page(remoteVehicle("vehicle-1", 1_000, name = "Car"), remoteVehicle("vehicle-2", 2_000, name = "Car"))
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
            fixture = fixture(adoption = {
                calls += 1
                if (fixture.persistence.outbox.isEmpty()) fixture.withOutbox(vehicleOutbox("vehicle-1"))
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
    fun unsupportedSchemaIsQuarantinedAndCursorAdvances() =
        runTest {
            val fixture = fixture()
            fixture.remote.pullHandler = { type, _ ->
                if (type == EntityType.VEHICLE) page(remoteVehicle("vehicle-1", 1_000, schemaVersion = 2)) else page()
            }

            fixture.controller.requestSync(SyncTrigger.AppForeground)
            advanceUntilIdle()

            assertEquals(QuarantineReason.UnsupportedSchemaVersion, fixture.persistence.quarantine.single().reason)
            assertFalse(fixture.persistence.vehicleIds.contains("vehicle-1"))
            assertEquals("vehicle-1", fixture.persistence.cursors.getValue(EntityType.VEHICLE).lastDocumentId?.value)
        }

    @Test
    fun malformedPayloadIsQuarantinedAndCursorAdvances() =
        runTest {
            val fixture = fixture()
            val malformed =
                RemoteDocument(EntityType.VEHICLE, EntityId("vehicle-1"), instant(1_000), "{\"schemaVersion\":1,\"id\":false}")
            fixture.remote.pullHandler = { type, _ -> if (type == EntityType.VEHICLE) page(malformed) else page() }

            fixture.controller.requestSync(SyncTrigger.AppForeground)
            advanceUntilIdle()

            val quarantine = fixture.persistence.quarantine.single()
            assertEquals(QuarantineReason.MalformedPayload, quarantine.reason)
            assertEquals(malformed.rawJson, quarantine.rawJson)
            assertEquals("vehicle-1", fixture.persistence.cursors.getValue(EntityType.VEHICLE).lastDocumentId?.value)
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
            fixture.persistence.states["vehicle-1"] = "FAILED_RETRYABLE"
            fixture.persistence.outbox[0] = fixture.persistence.outbox[0].copy(lastErrorCode = RemoteError.Unavailable.code)

            fixture.clock.advanceBy(7 * 24 * 60 * 60 * 1_000L)
            fixture.controller.requestSync(SyncTrigger.Periodic)
            advanceUntilIdle()
            assertEquals(SyncStatus.Pending(1), fixture.controller.status.value)
            assertEquals("FAILED_RETRYABLE", fixture.persistence.states.getValue("vehicle-1"))

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

    private fun TestScope.fixture(
        online: Boolean = true,
        now: Long = 0,
        adoption: suspend () -> Outcome<Unit, AppError> = { Outcome.Ok(Unit) },
    ): Fixture {
        val clock = TestClock(instant(now))
        val connectivity = TestConnectivity(online)
        val persistence = FakeSyncPersistence()
        val remote = FakeRemoteSyncSource()
        val controller =
            DefaultSyncController(
                scope = this,
                ownerContext = TestOwnerContext(OWNER),
                connectivity = connectivity,
                remote = remote,
                persistence = persistence,
                clock = clock,
                uuidGenerator = TestUuidGenerator(),
                jitter = JitterSource { _, _ -> 200 },
                adoption = adoption,
            )
        return Fixture(controller, persistence, remote, connectivity, clock)
    }

    private data class Fixture(
        val controller: DefaultSyncController,
        val persistence: FakeSyncPersistence,
        val remote: FakeRemoteSyncSource,
        val connectivity: TestConnectivity,
        val clock: TestClock,
    ) {
        fun withOutbox(row: OutboxRecord): Fixture {
            persistence.outbox += row
            persistence.states[row.entityId.value] = "PENDING"
            return this
        }
    }

    private companion object {
        val OWNER = OwnerId("owner-1")
    }
}

private class FakeRemoteSyncSource : RemoteSyncSource {
    val pushCalls = mutableListOf<EntitySnapshot>()
    val pushResults = ArrayDeque<Outcome<RemoteAck, RemoteError>>()
    val remoteIds = mutableSetOf<String>()
    val pullCursors = mutableListOf<RemoteCursor>()
    val vehiclePullCursors = mutableListOf<RemoteCursor>()
    val pagedDocuments = mutableMapOf<EntityType, List<RemoteDocument>>()
    var pullHandler: (EntityType, RemoteCursor) -> RemotePage = { _, cursor -> RemotePage(emptyList(), cursor, false) }
    var onPush: () -> Unit = {}
    var onPushSuspend: suspend () -> Unit = {}
    var activePushes = 0
    var maxConcurrentPushes = 0

    override suspend fun pushSnapshot(ownerId: OwnerId, snapshot: EntitySnapshot): Outcome<RemoteAck, RemoteError> {
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
                val start =
                    cursor.lastDocumentId?.value?.let { id -> documents.indexOfFirst { it.documentId.value == id } + 1 }
                        ?.coerceAtLeast(0) ?: 0
                val items = documents.drop(start).take(limit)
                RemotePage(
                    items = items,
                    nextCursor = items.lastOrNull()?.let { RemoteCursor(it.serverUpdatedAt, it.documentId) } ?: cursor,
                    hasMore = items.size == limit,
                )
            }
        return Outcome.Ok(if (page.items.isEmpty()) page.copy(nextCursor = cursor) else page)
    }
}

private class FakeSyncPersistence : SyncPersistence {
    val outbox = mutableListOf<OutboxRecord>()
    val states = mutableMapOf<String, String>()
    val cursors = mutableMapOf<EntityType, RemoteCursor>()
    val vehicleServerTimes = mutableMapOf<String, Long?>()
    val vehicleIds = mutableSetOf<String>()
    val fuelEntryIds = mutableSetOf<String>()
    val fuelEntryVehicles = mutableMapOf<String, String>()
    val deletedVehicles = mutableSetOf<String>()
    val quarantine = mutableListOf<QuarantineRecord>()
    var resetResult: Outcome<Unit, AppError> = Outcome.Ok(Unit)

    val visibleFuelEntryIds: Set<String>
        get() = fuelEntryIds.filterTo(mutableSetOf()) { fuelEntryVehicles[it] in vehicleIds }

    override suspend fun isOwnerDatabaseEmpty(ownerId: OwnerId): Boolean = vehicleIds.isEmpty() && fuelEntryIds.isEmpty() && outbox.isEmpty()

    override suspend fun dueOutbox(now: Instant, limit: Int): List<OutboxRecord> =
        outbox.filter { it.nextAttemptAt <= now && states[it.entityId.value] != "FAILED_POISONED" }.take(limit)

    override suspend fun markSyncing(row: OutboxRecord) {
        states[row.entityId.value] = "SYNCING"
    }

    override suspend fun confirmPush(row: OutboxRecord, serverUpdatedAt: Instant?) {
        val current = outbox.firstOrNull { it.entityId == row.entityId } ?: return
        if (current.localRevision == row.localRevision) {
            outbox.remove(current)
            states[row.entityId.value] = "SYNCED"
        } else {
            states[row.entityId.value] = "PENDING"
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
        outbox[index] = outbox[index].copy(attemptCount = attemptCount, nextAttemptAt = nextAttemptAt, lastErrorCode = errorCode)
        states[row.entityId.value] = if (poisoned) "FAILED_POISONED" else "FAILED_RETRYABLE"
    }

    override suspend fun cursor(entityType: EntityType): RemoteCursor = cursors[entityType] ?: RemoteCursor.INITIAL

    override suspend fun applyPullPage(
        ownerId: OwnerId,
        entityType: EntityType,
        records: List<PullRecord>,
        cursor: RemoteCursor,
    ) {
        records.forEach { record ->
            when (record) {
                is PullRecord.Quarantined -> quarantine += record.record
                is PullRecord.Vehicle -> {
                    val id = record.document.documentId.value
                    if (outbox.none { it.entityId.value == id } &&
                        (vehicleServerTimes[id] == null || record.document.serverUpdatedAt.toEpochMilliseconds() > requireNotNull(vehicleServerTimes[id]))
                    ) {
                        vehicleIds += id
                        vehicleServerTimes[id] = record.document.serverUpdatedAt.toEpochMilliseconds()
                        if (record.deletedAt == null) deletedVehicles -= id else deletedVehicles += id
                    }
                }
                is PullRecord.FuelEntry -> {
                    val id = record.document.documentId.value
                    if (outbox.none { it.entityId.value == id }) {
                        fuelEntryIds += id
                        fuelEntryVehicles[id] = record.vehicleId.value
                    }
                }
            }
        }
        cursors[entityType] = cursor
    }

    override suspend fun markConnectivityFailuresDue(now: Instant) {
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
                states[row.entityId.value] = "PENDING"
            }
        }
        return Outcome.Ok(Unit)
    }

    override suspend fun counts(): SyncCounts {
        var pending = 0
        var retryable = 0
        var poisoned = 0
        outbox.forEach { row ->
            when (states[row.entityId.value]) {
                "FAILED_POISONED" -> poisoned += 1
                "FAILED_RETRYABLE" -> if (row.lastErrorCode in setOf(RemoteError.Unavailable.code, RemoteError.DeadlineExceeded.code)) pending += 1 else retryable += 1
                else -> pending += 1
            }
        }
        return SyncCounts(pending, retryable, poisoned)
    }

    fun edit(entityId: String) {
        val index = outbox.indexOfFirst { it.entityId.value == entityId }
        outbox[index] = outbox[index].copy(localRevision = outbox[index].localRevision + 1)
        states[entityId] = "SYNCING"
    }
}

private fun vehicleOutbox(id: String, attemptCount: Int = 0): OutboxRecord =
    OutboxRecord(1, EntityType.VEHICLE, EntityId(id), vehicleJson(id, 0), 1, attemptCount, instant(0), null, false)

private fun ack(id: String): Outcome<RemoteAck, RemoteError> =
    Outcome.Ok(RemoteAck(EntityType.VEHICLE, EntityId(id), instant(1_000)))

private fun cursor(epochMillis: Long, id: String): RemoteCursor = RemoteCursor(instant(epochMillis), EntityId(id))

private fun page(vararg items: RemoteDocument, hasMore: Boolean = false): RemotePage =
    RemotePage(items.toList(), items.lastOrNull()?.let { RemoteCursor(it.serverUpdatedAt, it.documentId) } ?: RemoteCursor(instant(0), null), hasMore)

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

private fun remoteFuelEntry(id: String, vehicleId: String, serverUpdatedAt: Long): RemoteDocument =
    RemoteDocument(EntityType.FUEL_ENTRY, EntityId(id), instant(serverUpdatedAt), fuelJson(id, vehicleId, serverUpdatedAt))

private fun vehicleJson(
    id: String,
    updatedAt: Long,
    deleted: Boolean = false,
    name: String = id,
    schemaVersion: Int = 1,
): String =
    """{"id":"$id","ownerId":"owner-1","name":"$name","initialOdometerKm":0,"brand":null,"model":null,"fuelType":"GASOLINE","createdAt":0,"updatedAt":$updatedAt,"deleted":$deleted,"deletedAt":${if (deleted) updatedAt else "null"},"schemaVersion":$schemaVersion}"""

private fun fuelJson(id: String, vehicleId: String, updatedAt: Long): String =
    """{"id":"$id","ownerId":"owner-1","vehicleId":"$vehicleId","date":0,"odometerKm":1,"litersScaled":1,"pricePerLiterScaled":1,"totalCostMinor":1,"currency":"EUR","isFullTank":true,"hasMissedEntries":false,"odometerInconsistent":false,"notes":null,"createdAt":0,"updatedAt":$updatedAt,"deleted":false,"deletedAt":null,"schemaVersion":1}"""

private fun instant(epochMillis: Long): Instant = Instant.fromEpochMilliseconds(epochMillis)

private class TestClock(initial: Instant) : AppClock {
    private var current = initial

    override fun now(): Instant = current

    fun advanceBy(millis: Long) {
        current = Instant.fromEpochMilliseconds(current.toEpochMilliseconds() + millis)
    }
}

private class TestConnectivity(initiallyOnline: Boolean) : ConnectivityObserver {
    private val mutable = MutableStateFlow(initiallyOnline)
    override val isOnline: StateFlow<Boolean> = mutable

    fun set(value: Boolean) {
        mutable.value = value
    }
}

private class TestOwnerContext(initial: OwnerId) : OwnerContext {
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
