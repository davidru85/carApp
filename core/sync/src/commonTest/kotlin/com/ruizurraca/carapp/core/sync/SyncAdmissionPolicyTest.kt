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
import kotlinx.coroutines.delay
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
import kotlin.test.assertFalse
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
 * Time is driven explicitly. `advanceUntilIdle()` is deliberately avoided wherever a window is being
 * measured: it advances until no task remains, so it would fast-forward straight through the 2 s and
 * 30 s windows these tests are about. `runCurrent()` plus `advanceTimeBy` keeps every boundary exact,
 * and the assertions read cycles at the transport, which is what a window actually governs.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncAdmissionPolicyTest {
    @Test
    fun postWriteDebounceCoalescesWritesInsideOneWindowIntoASingleCycle() =
        runTest {
            val fixture = fixture()

            // Three writes inside one debounce window. Coalescing is the point of `§9.8`: the delay is
            // measured from the last write, so all three share one cycle.
            fixture.controller.requestSync(SyncTrigger.PostWriteDebounce)
            runCurrent()
            advanceTimeBy(SYNC_POST_WRITE_DEBOUNCE_MS - 1)
            runCurrent()
            fixture.controller.requestSync(SyncTrigger.PostWriteDebounce)
            advanceTimeBy(1)
            runCurrent()
            fixture.controller.requestSync(SyncTrigger.PostWriteDebounce)
            runCurrent()

            assertEquals(0, fixture.remote.cycles, "no cycle may start before the debounce window elapses")

            advanceTimeBy(SYNC_POST_WRITE_DEBOUNCE_MS)
            runCurrent()

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
            runCurrent()
            advanceTimeBy(SYNC_POST_WRITE_DEBOUNCE_MS - 1)
            runCurrent()
            assertEquals(
                0,
                fixture.remote.cycles,
                "one millisecond early is still inside the debounce window",
            )

            advanceTimeBy(1)
            runCurrent()
            assertEquals(1, fixture.remote.cycles, "the debounce boundary admits the cycle")
        }

    @Test
    fun automaticTriggersRespectTheMinimumIntervalFloor() =
        runTest {
            val fixture = fixture()

            fixture.controller.requestSync(SyncTrigger.AppForeground)
            runCurrent()
            assertEquals(1, fixture.remote.cycles, "a first automatic trigger has no preceding cycle")

            fixture.controller.requestSync(SyncTrigger.AppForeground)
            runCurrent()
            advanceTimeBy(SYNC_MIN_AUTOMATIC_INTERVAL_MS - 1)
            runCurrent()
            assertEquals(
                1,
                fixture.remote.cycles,
                "the floor MUST suppress a second automatic cycle inside the minimum interval",
            )

            advanceTimeBy(1)
            runCurrent()
            assertEquals(2, fixture.remote.cycles, "the floor expiry admits exactly one more cycle")
        }

    @Test
    fun aTriggerInsideTheFloorIsServedRatherThanDropped() =
        runTest {
            val fixture = fixture()

            fixture.controller.requestSync(SyncTrigger.AppForeground)
            runCurrent()
            assertEquals(1, fixture.remote.cycles)

            // The trigger arrives inside the floor. It MUST be deferred, not discarded: dropping it
            // would leave the write outstanding until the next trigger, which `§9.2` and P2 forbid.
            fixture.controller.requestSync(SyncTrigger.PostWriteDebounce)
            runCurrent()
            advanceTimeBy(SYNC_MIN_AUTOMATIC_INTERVAL_MS - 1)
            runCurrent()
            assertEquals(1, fixture.remote.cycles)

            advanceTimeBy(1)
            runCurrent()
            assertEquals(2, fixture.remote.cycles, "a deferred trigger is served, never dropped")
        }

    @Test
    fun pullToRefreshBypassesTheFloorAndStillSerializesOnTheMutex() =
        runTest {
            val fixture = fixture()

            fixture.controller.requestSync(SyncTrigger.AppForeground)
            runCurrent()
            assertEquals(1, fixture.remote.cycles)

            // A user-initiated refresh must not be held back by the floor (`§9.8`), and it still runs
            // one cycle at a time because it serializes on the same mutex.
            val result = fixture.controller.sync(SyncTrigger.PullToRefresh)
            runCurrent()
            assertEquals(Outcome.Ok(Unit), result)
            assertEquals(2, fixture.remote.cycles, "pull-to-refresh bypasses the minimum interval")
            assertEquals(1, fixture.remote.maxConcurrentCycles, "pull-to-refresh never bypasses the mutex")
        }

    @Test
    fun theFloorIsMeasuredFromTheStartOfThePreviousAutomaticCycle() =
        runTest {
            // The first cycle's remote work takes real virtual time. The floor is anchored to the
            // moment the cycle reached the remote steps, so it expires 30 s after that start and not
            // 30 s after the cycle finished - otherwise a slow cycle would push the next one further
            // out the longer it took, which is not what "minimum interval between cycles" means.
            val fixture = fixture(pullDelayMillis = FIRST_CYCLE_PULL_MILLIS)

            fixture.controller.requestSync(SyncTrigger.AppForeground)
            runCurrent()
            advanceTimeBy(FIRST_CYCLE_PULL_MILLIS)
            runCurrent()
            assertEquals(1, fixture.remote.cycles, "the first cycle completed")

            // 30 s after the start of that cycle; the cycle itself ended
            // `FIRST_CYCLE_PULL_MILLIS` after it started.
            fixture.controller.requestSync(SyncTrigger.AppForeground)
            runCurrent()
            advanceTimeBy(SYNC_MIN_AUTOMATIC_INTERVAL_MS - FIRST_CYCLE_PULL_MILLIS)
            runCurrent()

            assertEquals(
                2,
                fixture.remote.cycles,
                "the floor is measured from the previous cycle's start, not from its completion",
            )
        }

    @Test
    fun aDeferredConnectivityRecoveredStillRunsItsReasonDependentStep() =
        runTest {
            val fixture = fixture()

            fixture.controller.requestSync(SyncTrigger.AppForeground)
            runCurrent()

            // Deferred by the floor, but the cycle that serves it MUST still make connectivity failures
            // due, because that step is reason-dependent (`§9.7`, `§9.8`).
            fixture.controller.requestSync(SyncTrigger.ConnectivityRecovered)
            runCurrent()
            advanceTimeBy(SYNC_MIN_AUTOMATIC_INTERVAL_MS)
            runCurrent()

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
            runCurrent()
            assertEquals(1, fixture.remote.cycles)

            // `sync()` honours the same admission policy as `requestSync`; there is no second path
            // around the floor. It resolves with the serving cycle's outcome rather than hanging.
            val awaited = async { fixture.controller.sync(SyncTrigger.PostWriteDebounce) }
            runCurrent()
            advanceTimeBy(SYNC_MIN_AUTOMATIC_INTERVAL_MS)
            runCurrent()

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
            runCurrent()
            releaseCycle.complete(Unit)
            runCurrent()
            advanceUntilIdle()

            assertEquals(
                2,
                fixture.remote.cycles,
                "the in-flight cycle plus exactly one follow-up serve both triggers",
            )
        }

    @Test
    fun shutdownCompletesATriggerParkedOnAnAdmissionWindow() =
        runTest {
            val fixture = fixture()

            fixture.controller.requestSync(SyncTrigger.AppForeground)
            runCurrent()

            // Park an automatic trigger inside the floor, then close the graph (`D-172`): the awaitable
            // caller lives outside the graph scope, so shutdown MUST complete it.
            val parked = async { fixture.controller.sync(SyncTrigger.Periodic) }
            runCurrent()
            fixture.controller.shutdown()

            val result = withTimeoutOrNull(5.seconds) { parked.await() }
            assertEquals(
                Outcome.Err(PersistenceError.DatabaseUnavailable),
                result,
                "shutdown MUST complete a trigger parked on an admission window",
            )
        }

    @Test
    fun pullToRefreshDoesNotServeAnAutomaticTriggerBeforeItsWindowBoundary() =
        runTest {
            val fixture = fixture()

            fixture.controller.requestSync(SyncTrigger.AppForeground)
            runCurrent()
            assertEquals(1, fixture.remote.cycles)

            val parked = async { fixture.controller.sync(SyncTrigger.PostWriteDebounce) }
            runCurrent()
            val manual = async { fixture.controller.sync(SyncTrigger.PullToRefresh) }
            runCurrent()

            assertEquals(Outcome.Ok(Unit), manual.await())
            assertFalse(parked.isCompleted, "manual refresh MUST leave the automatic request parked")
            assertEquals(2, fixture.remote.cycles, "only the initial and manual cycles may have run")

            advanceTimeBy(SYNC_MIN_AUTOMATIC_INTERVAL_MS)
            runCurrent()

            assertEquals(Outcome.Ok(Unit), parked.await())
            assertEquals(3, fixture.remote.cycles, "the automatic request runs at the floor boundary")
        }

    @Test
    fun shutdownBetweenClaimPublicationAndAdmissionReturnCompletesTheAwaiter() =
        runTest {
            lateinit var controller: DefaultSyncController
            val fixture =
                fixture(
                    hooks =
                        SyncConcurrencyHooks(
                            afterClaimPublished = { controller.shutdown() },
                        ),
                )
            controller = fixture.controller

            val result = controller.sync(SyncTrigger.AppForeground)

            assertEquals(Outcome.Err(PersistenceError.DatabaseUnavailable), result)
            assertEquals(0, fixture.remote.cycles, "shutdown MUST prevent the claimed cycle from starting")
        }

    @Test
    fun shutdownDuringFollowUpPromotionCompletesThePromotedAwaiter() =
        runTest {
            lateinit var controller: DefaultSyncController
            val fixture =
                fixture(
                    hooks =
                        SyncConcurrencyHooks(
                            afterFollowUpPromotionPublished = { controller.shutdown() },
                        ),
                )
            controller = fixture.controller
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            fixture.remote.onPull = {
                entered.complete(Unit)
                release.await()
            }

            val first = async { controller.sync(SyncTrigger.AppForeground) }
            entered.await()
            val followUp = async { controller.sync(SyncTrigger.PostWriteDebounce) }
            runCurrent()
            release.complete(Unit)
            runCurrent()

            assertEquals(Outcome.Ok(Unit), first.await())
            assertEquals(Outcome.Err(PersistenceError.DatabaseUnavailable), followUp.await())
        }

    @Test
    fun aWindowThatOpensDuringAManualCycleStillServesItsParkedTrigger() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val fixture =
                fixture(
                    adoption = {
                        gate.await()
                        error("adoption failed before the cycle reached its remote steps")
                    },
                )

            // The post-write trigger parks and arms the 2 s debounce window.
            val parked = async { fixture.controller.sync(SyncTrigger.PostWriteDebounce) }
            runCurrent()

            // A manual refresh claims a cycle that stays inside `adoption()`, so the parked batch is
            // still waiting while a cycle is running. `PullToRefresh` never drains it (`D-186`).
            val manual = async { fixture.controller.sync(SyncTrigger.PullToRefresh) }
            runCurrent()

            // The debounce window opens while that cycle is running, so its timer finds
            // `cycleRunning` true and serves nothing. The window is spent from here on.
            advanceTimeBy(SYNC_POST_WRITE_DEBOUNCE_MS)
            runCurrent()

            // The manual cycle fails before the remote steps, so it arms no floor and schedules no
            // adoption retry: no timer is left that could ever reach the parked batch.
            gate.complete(Unit)
            runCurrent()
            manual.await()

            advanceTimeBy(SYNC_MIN_AUTOMATIC_INTERVAL_MS * 4)
            advanceUntilIdle()

            assertTrue(
                parked.isCompleted,
                "a parked trigger MUST be served once its window is open and no cycle is running",
            )
        }

    private fun TestScope.fixture(
        pullDelayMillis: Long = 0,
        hooks: SyncConcurrencyHooks = SyncConcurrencyHooks(),
        adoption: suspend () -> Outcome<Unit, AppError> = { Outcome.Ok(Unit) },
    ): AdmissionFixture {
        val clock = AdmissionClock(Instant.fromEpochMilliseconds(0))
        val connectivity = AdmissionConnectivity(true)
        val persistence = AdmissionPersistence()
        val remote = AdmissionRemote(pullDelayMillis)
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
                adoption = adoption,
                concurrencyHooks = hooks,
            )
        return AdmissionFixture(controller, persistence, remote, connectivity, clock)
    }
}

/** Virtual milliseconds the first cycle's remote pull consumes in the floor-anchor test. */
private const val FIRST_CYCLE_PULL_MILLIS = 5_000L

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
private class AdmissionRemote(
    private val pullDelayMillis: Long,
) : RemoteSyncSource {
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
            if (pullDelayMillis > 0) delay(pullDelayMillis)
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
