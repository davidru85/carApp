package com.ruizurraca.carapp.core.sync

import com.ruizurraca.carapp.core.common.AppClock
import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.ConnectivityObserver
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.OwnerContext
import com.ruizurraca.carapp.core.common.PersistenceError
import com.ruizurraca.carapp.core.common.RemoteError
import com.ruizurraca.carapp.core.common.SYNC_MIN_AUTOMATIC_INTERVAL_MS
import com.ruizurraca.carapp.core.common.SYNC_POST_WRITE_DEBOUNCE_MS
import com.ruizurraca.carapp.core.common.SyncTrigger
import com.ruizurraca.carapp.core.common.UuidGenerator
import com.ruizurraca.carapp.core.model.OwnerId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The `docs/CONTRACTS.md §9.8` admission policy of `DefaultSyncController`.
 *
 * `SYNC_POST_WRITE_DEBOUNCE_MS` (2 s) coalesces local mutations into one cycle and
 * `SYNC_MIN_AUTOMATIC_INTERVAL_MS` (30 s) is the floor between automatic cycles. Pull-to-refresh
 * bypasses the floor and never the mutex.
 *
 * The waits are built from `delay`, so the scheduler's virtual time drives them and the boundaries
 * are asserted exactly: one millisecond early stays inside the window, the boundary itself admits
 * the cycle. The assertions read the injected clock, so a deferred trigger is proven to be *served*
 * rather than merely late.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncAdmissionPolicyTest {
    @Test
    fun postWriteDebounceCoalescesWritesInsideOneWindowIntoASingleCycle() =
        runTest {
            val fixture = fixture()

            // Three writes inside one debounce window. Coalescing is the point of `§9.8`: the delay
            // is measured from the last write, so all three share one cycle.
            fixture.controller.requestSync(SyncTrigger.PostWriteDebounce)
            advanceTimeBy(SYNC_POST_WRITE_DEBOUNCE_MS - 1)
            runCurrent()
            fixture.controller.requestSync(SyncTrigger.PostWriteDebounce)
            advanceTimeBy(1)
            runCurrent()
            fixture.controller.requestSync(SyncTrigger.PostWriteDebounce)
            runCurrent()

            assertEquals(
                0,
                fixture.remote.cycles,
                "no cycle may start before the debounce window elapses",
            )

            advanceTimeBy(SYNC_POST_WRITE_DEBOUNCE_MS)
            advanceUntilIdle()

            assertEquals(
                1,
                fixture.remote.cycles,
                "writes inside one debounce window MUST coalesce into a single cycle",
            )
        }

    @Test
    fun postWriteCycleStartsExactlyAtTheDebounceBoundary() =
        runTest {
            val fixture = fixture()

            fixture.controller.requestSync(SyncTrigger.PostWriteDebounce)
            advanceTimeBy(SYNC_POST_WRITE_DEBOUNCE_MS - 1)
            runCurrent()
            assertEquals(
                0,
                fixture.remote.cycles,
                "one millisecond early is still inside the debounce window",
            )

            advanceTimeBy(1)
            advanceUntilIdle()
            assertEquals(1, fixture.remote.cycles, "the debounce boundary admits the cycle")
        }

    @Test
    fun automaticTriggersRespectTheMinimumIntervalFloor() =
        runTest {
            val fixture = fixture()

            fixture.controller.requestSync(SyncTrigger.AppForeground)
            advanceUntilIdle()
            assertEquals(
                1,
                fixture.remote.cycles,
                "the first automatic cycle is admitted without a preceding cycle to space it from",
            )

            fixture.controller.requestSync(SyncTrigger.AppForeground)
            advanceTimeBy(SYNC_MIN_AUTOMATIC_INTERVAL_MS - 1)
            runCurrent()
            assertEquals(
                1,
                fixture.remote.cycles,
                "the floor MUST suppress a second automatic cycle inside the minimum interval",
            )

            advanceTimeBy(1)
            advanceUntilIdle()
            assertEquals(2, fixture.remote.cycles, "the floor expiry admits exactly one more cycle")
        }

    @Test
    fun aTriggerInsideTheFloorIsServedRatherThanDropped() =
        runTest {
            val fixture = fixture()

            fixture.controller.requestSync(SyncTrigger.AppForeground)
            advanceUntilIdle()
            assertEquals(1, fixture.remote.cycles)

            // The trigger arrives inside the floor. It MUST be deferred, not discarded: dropping it
            // would leave the write outstanding until the next trigger, which `§9.2` and P2 forbid.
            fixture.controller.requestSync(SyncTrigger.PostWriteDebounce)
            advanceTimeBy(SYNC_MIN_AUTOMATIC_INTERVAL_MS - 1)
            runCurrent()
            assertEquals(1, fixture.remote.cycles)

            advanceTimeBy(1)
            advanceUntilIdle()
            assertEquals(2, fixture.remote.cycles, "a deferred trigger is served, never dropped")
        }

    @Test
    fun pullToRefreshBypassesTheFloorAndStillSerializesOnTheMutex() =
        runTest {
            val fixture = fixture()

            fixture.controller.requestSync(SyncTrigger.AppForeground)
            advanceUntilIdle()
            assertEquals(1, fixture.remote.cycles)

            // A user-initiated refresh must not be held back by the floor (`§9.8`) ...
            val result = fixture.controller.sync(SyncTrigger.PullToRefresh)
            advanceUntilIdle()
            assertEquals(Outcome.Ok(Unit), result)
            assertEquals(2, fixture.remote.cycles, "pull-to-refresh bypasses the minimum interval")

            // ... and it still runs one cycle at a time.
            assertEquals(1, fixture.remote.maxConcurrentCycles, "pull-to-refresh never bypasses the mutex")
        }

    @Test
    fun theFloorIsMeasuredFromTheStartOfThePreviousAutomaticCycle() =
        runTest {
            val fixture = fixture()

            fixture.controller.requestSync(SyncTrigger.PostWriteDebounce)
            advanceTimeBy(SYNC_POST_WRITE_DEBOUNCE_MS)
            advanceUntilIdle()
            assertEquals(1, fixture.remote.cycles)

            // The floor runs from that cycle's start, so the next automatic trigger is admitted
            // `SYNC_MIN_AUTOMATIC_INTERVAL_MS` after it, not after its completion.
            fixture.controller.requestSync(SyncTrigger.AppForeground)
            advanceTimeBy(SYNC_MIN_AUTOMATIC_INTERVAL_MS - SYNC_POST_WRITE_DEBOUNCE_MS - 1)
            runCurrent()
            assertEquals(1, fixture.remote.cycles)

            advanceTimeBy(1)
            advanceUntilIdle()
            assertEquals(2, fixture.remote.cycles)
        }

    @Test
    fun aDeferredConnectivityRecoveredStillRunsItsReasonDependentStep() =
        runTest {
            val fixture = fixture()

            fixture.controller.requestSync(SyncTrigger.AppForeground)
            advanceUntilIdle()

            // Deferred by the floor, but the cycle it is served by MUST still make connectivity
            // failures due, because that step is reason-dependent (`§9.7`, `§9.8`).
            fixture.controller.requestSync(SyncTrigger.ConnectivityRecovered)
            advanceUntilIdle()

            assertTrue(
                fixture.persistence.connectivityDueCalls.isNotEmpty(),
                "a deferred ConnectivityRecovered MUST still reach markConnectivityFailuresDue",
            )
        }

    @Test
    fun anAwaitableAutomaticTriggerReturnsTheOutcomeOfTheCycleThatServesIt() =
        runTest {
            val fixture = fixture()

            fixture.controller.requestSync(SyncTrigger.AppForeground)
            advanceUntilIdle()

            // `sync()` honours the same admission policy as `requestSync`; there is no second path
            // around the floor. It resolves with the serving cycle's outcome rather than hanging.
            val awaited = async { fixture.controller.sync(SyncTrigger.PostWriteDebounce) }
            advanceTimeBy(SYNC_MIN_AUTOMATIC_INTERVAL_MS)
            advanceUntilIdle()

            assertEquals(Outcome.Ok(Unit), awaited.await())
            assertEquals(2, fixture.remote.cycles)
        }

    @Test
    fun aPostWriteDuringAnInFlightCycleJoinsThePendingFollowUp() =
        runTest {
            val fixture = fixture()
            val firstCycleStarted = CompletableDeferred<Unit>()
            val releaseCycle = CompletableDeferred<Unit>()
            fixture.remote.onPull = {
                firstCycleStarted.complete(Unit)
                withContext(NonCancellable) { releaseCycle.await() }
            }

            fixture.controller.requestSync(SyncTrigger.AppForeground)
            firstCycleStarted.await()

            // A write that lands while a cycle is running is coalesced into the single pending
            // follow-up rather than starting an unthrottled second cycle (`§9.1`).
            fixture.controller.requestSync(SyncTrigger.PostWriteDebounce)
            releaseCycle.complete(Unit)
            advanceUntilIdle()

            assertEquals(
                2,
                fixture.remote.cycles,
                "the in-flight cycle plus exactly one follow-up serve both triggers",
            )
        }

    @Test
    fun shutdownCompletesAnAutomaticTriggerParkedOnTheFloor() =
        runTest {
            val fixture = fixture()

            fixture.controller.requestSync(SyncTrigger.AppForeground)
            advanceUntilIdle()

            // Park an automatic trigger inside the floor, then close the graph (`D-172`): the
            // awaitable caller lives outside the graph scope, so shutdown MUST complete it.
            val parked = async { fixture.controller.sync(SyncTrigger.Periodic) }
            runCurrent()
            fixture.controller.shutdown()
            advanceUntilIdle()

            val result = withTimeoutOrNull(5.seconds) { parked.await() }
            assertEquals(
                Outcome.Err(PersistenceError.DatabaseUnavailable),
                result,
                "shutdown MUST complete a trigger parked on an admission window",
            )
        }

    private fun TestScope.fixture(): AdmissionFixture {
        val clock = AdmissionClock(Instant.fromEpochMilliseconds(0))
        val connectivity = AdmissionConnectivity(true)
        val persistence = AdmissionPersistence()
        val remote = AdmissionRemote()
        val controller =
            DefaultSyncController(
                scope = this,
                ownerContext = AdmissionOwnerContext(),
                connectivity = connectivity,
                remote = remote,
                persistence = persistence,
                clock = clock,
                uuidGenerator = AdmissionUuidGenerator(),
                jitter = JitterSource { _, _ -> 200 },
            )
        return AdmissionFixture(controller, persistence, remote, connectivity, clock)
    }
}

private data class AdmissionFixture(
    val controller: DefaultSyncController,
    val persistence: AdmissionPersistence,
    val remote: AdmissionRemote,
    val connectivity: AdmissionConnectivity,
    val clock: AdmissionClock,
)

private class AdmissionClock(
    initial: Instant,
) : AppClock {
    private var current = initial

    override fun now(): Instant = current

    fun advanceBy(millis: Long) {
        current = Instant.fromEpochMilliseconds(current.toEpochMilliseconds() + millis)
    }
}

private class AdmissionConnectivity(
    initiallyOnline: Boolean,
) : ConnectivityObserver {
    private val mutable = MutableStateFlow(initiallyOnline)
    override val isOnline: StateFlow<Boolean> = mutable

    fun set(value: Boolean) {
        mutable.value = value
    }
}

private class AdmissionOwnerContext : OwnerContext {
    private val mutable = MutableStateFlow(OwnerId("owner-1"))
    override val current: OwnerId get() = mutable.value

    override fun observe(): Flow<OwnerId> = mutable
}

private class AdmissionUuidGenerator : UuidGenerator {
    private var sequence = 0

    override fun newId(): String {
        sequence += 1
        return "00000000-0000-4000-8000-${sequence.toString().padStart(12, '0')}"
    }
}

/** Counts cycles at the transport, which is the only observable an admission window governs. */
private class AdmissionRemote : RemoteSyncSource {
    var cycles = 0
    var maxConcurrentCycles = 0
    var onPull: (suspend () -> Unit)? = null
    private var activeCycles = 0

    override suspend fun pushSnapshot(
        ownerId: OwnerId,
        snapshot: EntitySnapshot,
    ): Outcome<RemoteAck, RemoteError> = Outcome.Ok(RemoteAck(snapshot.entityType, snapshot.entityId, NOW))

    override suspend fun pullChanges(
        ownerId: OwnerId,
        entityType: EntityType,
        cursor: RemoteCursor,
        limit: Int,
    ): Outcome<RemotePage, RemoteError> {
        if (entityType == EntityType.VEHICLE) {
            cycles += 1
            activeCycles += 1
            maxConcurrentCycles = maxOf(maxConcurrentCycles, activeCycles)
            onPull?.invoke()
            activeCycles -= 1
        }
        return Outcome.Ok(RemotePage(items = emptyList(), nextCursor = cursor, hasMore = false))
    }

    private companion object {
        val NOW = Instant.fromEpochMilliseconds(1_000)
    }
}

private class AdmissionPersistence : SyncPersistence {
    val connectivityDueCalls = mutableListOf<Instant>()

    override suspend fun isOwnerDatabaseEmpty(ownerId: OwnerId): Boolean = true

    override suspend fun dueOutbox(
        now: Instant,
        limit: Int,
    ): List<OutboxRecord> = emptyList()

    override suspend fun markSyncing(row: OutboxRecord) = Unit

    override suspend fun confirmPush(
        row: OutboxRecord,
        serverUpdatedAt: Instant?,
    ) = Unit

    override suspend fun failPush(
        row: OutboxRecord,
        attemptCount: Int,
        nextAttemptAt: Instant,
        errorCode: String,
        poisoned: Boolean,
        cycleId: CycleId,
    ) = Unit

    override suspend fun markConnectivityFailuresDue(now: Instant) {
        connectivityDueCalls += now
    }

    override suspend fun resetFailed(now: Instant): Outcome<Unit, AppError> = Outcome.Ok(Unit)

    override suspend fun cursor(entityType: EntityType): RemoteCursor = RemoteCursor.INITIAL

    override suspend fun applyPullPage(
        ownerId: OwnerId,
        entityType: EntityType,
        records: List<PullRecord>,
        cursor: RemoteCursor,
    ): List<QuarantineRecord> = emptyList()

    override suspend fun counts(): SyncCounts = SyncCounts(pending = 0, retryable = 0, poisoned = 0)
}
