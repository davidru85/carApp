package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.PersistenceError
import com.ruizurraca.carapp.core.common.RemoteError
import com.ruizurraca.carapp.core.common.SyncTrigger
import com.ruizurraca.carapp.core.database.DatabaseFactory
import com.ruizurraca.carapp.core.database.DatabaseHandle
import com.ruizurraca.carapp.core.model.OwnerId
import com.ruizurraca.carapp.core.sync.EntitySnapshot
import com.ruizurraca.carapp.core.sync.EntityType
import com.ruizurraca.carapp.core.sync.RemoteAck
import com.ruizurraca.carapp.core.sync.RemoteCursor
import com.ruizurraca.carapp.core.sync.RemotePage
import com.ruizurraca.carapp.core.sync.RemoteSyncSource
import com.ruizurraca.carapp.core.sync.SyncController
import com.ruizurraca.carapp.core.testing.FakeConnectivityObserver
import com.ruizurraca.carapp.core.testing.FakeOwnerContext
import com.ruizurraca.carapp.core.testing.InMemoryDatabaseFactory
import com.ruizurraca.carapp.shared.testing.testAppGraphDependencies
import com.ruizurraca.carapp.shared.testing.testAppProviders
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** Bounded yields that let a suspension point be reached without waiting on a real clock. */
private const val RELEASE_OBSERVATION_YIELDS = 8

/**
 * `E3-17` / `D-172`. `DefaultAppGraph.close()` cancels `graphScope` and then closes the
 * `DatabaseHandle` in the same call. `cancel()` does not join, and `E3-03` put long-running sync
 * cycles on that scope, so a cycle that is still unwinding when the handle closes will make its next
 * SQLite call against a released driver.
 *
 * **Why the cycle here is deliberately non-cooperative.** For work that cancels promptly, `cancel()`
 * already unwinds the cycle before the next statement runs, so the buggy and fixed versions look
 * identical and the test would prove nothing. The hazard is the cycle that is *already inside* a call
 * when cancellation arrives - a SQLite statement or a remote round trip that does not abandon its
 * work on the first cancellation signal. These tests model exactly that with `NonCancellable`, which
 * is the harshest honest stand-in for it.
 *
 * The invariant asserted is the one that prevents the race, in terms a reader can check: **the handle
 * is not released while graph-owned work is still running.** Both production close paths are covered
 * as they really are: the Kotlin path (`MainActivity.onCleared()`) and the Swift path
 * (`SwiftAppGraph.close()`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppGraphCloseSafetyTest {
    @Test
    fun theHandleIsNotReleasedWhileANonCancellableCycleIsStillRunning() =
        runTest {
            val events = mutableListOf<String>()
            val remote = NonCancellableRemote { events += CYCLE_FINISHED }
            val factory = OrderRecordingDatabaseFactory(events)
            val graph = buildGraph(factory, remote)

            try {
                graph.syncController().requestSync(SyncTrigger.PullToRefresh)
                awaitCondition("the cycle to reach the remote call") { remote.entered.isCompleted }

                graph.close()

                assertFalse(
                    HANDLE_CLOSED in events,
                    "the handle was released while the cycle was still running; events=$events",
                )

                // Let the cycle finish. The handle must be released once - and only once - the graph's
                // work is really done, which is what the completion-ordered release provides.
                remote.release()
                awaitCondition("the handle to be released after the cycle finished") { factory.closeCalls == 1 }
                assertEquals(listOf(CYCLE_FINISHED, HANDLE_CLOSED), events)
            } finally {
                remote.release()
                factory.closeOwningFactory()
            }
        }

    @Test
    fun theSwiftClosePathIsSafeToo() =
        runTest {
            val events = mutableListOf<String>()
            val remote = NonCancellableRemote { events += CYCLE_FINISHED }
            val factory = OrderRecordingDatabaseFactory(events)
            val dependencies = graphDependencies(factory, remote)
            val graph = buildAppGraph(isDebugBuild = true, providers = testAppProviders(dependencies))
            val swiftGraph = wrapAppGraphForSwift(graph, dependencies.dispatchers)

            try {
                graph.syncController().requestSync(SyncTrigger.PullToRefresh)
                awaitCondition("the cycle to reach the remote call") { remote.entered.isCompleted }

                swiftGraph.close()
                swiftGraph.close()

                assertFalse(
                    HANDLE_CLOSED in events,
                    "the handle was released while the cycle was still running; events=$events",
                )

                remote.release()
                awaitCondition("the handle to be released after the cycle finished") { factory.closeCalls == 1 }
                assertEquals(listOf(CYCLE_FINISHED, HANDLE_CLOSED), events)
                assertEquals(1, factory.closeCalls, "the handle is released exactly once")
            } finally {
                remote.release()
                factory.closeOwningFactory()
            }
        }

    @Test
    fun awaitClosedSuspendsUntilTheHandleIsActuallyReleased() =
        runTest {
            val factory = OrderRecordingDatabaseFactory(mutableListOf())
            val remote = NonCancellableRemote {}
            val graph = buildGraph(factory, remote)

            try {
                val controller: SyncController = graph.syncController()
                val running = async { controller.sync(SyncTrigger.PullToRefresh) }
                awaitCondition("the cycle to reach the remote call") { remote.entered.isCompleted }

                // `close()` returns as soon as the scope is cancelled; the handle is released later by
                // the bounded waiter. A caller that deletes the database file needs the release, not
                // the cancellation, so `awaitClosed()` MUST still be suspended here.
                val closed = async { graph.awaitClosed() }
                // Let `awaitClosed()` reach its suspension point. The cycle is still blocked on
                // `remote`, so the scope cannot drain and the handle cannot have been released.
                repeat(RELEASE_OBSERVATION_YIELDS) { yield() }
                assertFalse(
                    closed.isCompleted,
                    "awaitClosed() MUST NOT report a release the graph has not performed yet",
                )
                assertEquals(0, factory.closeCalls, "the handle MUST still be open while the cycle runs")

                remote.release()
                awaitCondition("the release to complete") { closed.isCompleted }
                closed.await()
                assertEquals(
                    1,
                    factory.closeCalls,
                    "the single DatabaseHandle MUST be released exactly once",
                )
                // The cycle that was running when the closure started is refused, not left suspended:
                // that is the same `D-172` guarantee, observed through the second caller.
                val outcome = running.await()
                assertTrue(
                    outcome is Outcome.Err && outcome.error == PersistenceError.DatabaseUnavailable,
                    "expected a closed DatabaseUnavailable outcome, got $outcome",
                )
            } finally {
                remote.release()
                factory.closeOwningFactory()
            }
        }

    @Test
    fun anInFlightSyncAwaiterReturnsInsteadOfHangingWhenTheGraphCloses() =
        runTest {
            val factory = OrderRecordingDatabaseFactory(mutableListOf())
            val remote = NonCancellableRemote {}
            val graph = buildGraph(factory, remote)

            try {
                val controller: SyncController = graph.syncController()
                val awaiting = async { controller.sync(SyncTrigger.PullToRefresh) }
                awaitCondition("the cycle to reach the remote call") { remote.entered.isCompleted }

                graph.close()
                remote.release()

                // The caller is outside `graphScope`, so cancelling that scope does not cancel it. The
                // shutdown has to complete its deferred with a closed outcome or it suspends forever.
                val result = async { awaiting.await() }
                awaitCondition("the sync() caller to return") { result.isCompleted }
                val outcome = result.await()
                assertTrue(
                    outcome is Outcome.Err && outcome.error == PersistenceError.DatabaseUnavailable,
                    "expected a closed DatabaseUnavailable outcome, got $outcome",
                )
            } finally {
                remote.release()
                factory.closeOwningFactory()
            }
        }

    private fun buildGraph(
        factory: DatabaseFactory,
        remote: RemoteSyncSource,
    ) = buildAppGraph(isDebugBuild = true, providers = testAppProviders(graphDependencies(factory, remote)))

    private fun graphDependencies(
        factory: DatabaseFactory,
        remote: RemoteSyncSource,
    ) = testAppGraphDependencies(
        databaseFactory = factory,
        remoteSyncSource = remote,
        // A real owner is required: a cycle refused for `LOCAL_OWNER` or offline never reaches the
        // remote call, so it could not expose the close race at all.
        ownerContext = FakeOwnerContext(OwnerId(SYNCING_OWNER)),
        connectivityObserver = FakeConnectivityObserver(initiallyOnline = true),
    )

    @Test
    fun theHandleIsStillReleasedWhenACycleNeverObservesCancellation() =
        runTest {
            val events = mutableListOf<String>()
            val remote = NonCancellableRemote { events += CYCLE_FINISHED }
            val factory = OrderRecordingDatabaseFactory(events)
            val graph = buildGraph(factory, remote)

            try {
                graph.syncController().requestSync(SyncTrigger.PullToRefresh)
                awaitCondition("the cycle to reach the remote call") { remote.entered.isCompleted }

                graph.close()

                // The cycle is parked in a non-cancellable section, so the ordered release cannot
                // happen yet. `D-172` accepts that window and bounds it, and this asserts the bound
                // really fires rather than trusting it: work that ignored cancellation must not hold
                // the handle for the life of the process.
                val startedAt = TimeSource.Monotonic.markNow()
                awaitCondition("the bounded backstop to release the handle", timeout = 15.seconds) {
                    factory.closeCalls == 1
                }
                val elapsed = startedAt.elapsedNow()
                assertTrue(
                    elapsed >= (BACKSTOP_MILLIS - 1_000).milliseconds,
                    "the handle was released before the accepted window elapsed: $elapsed",
                )
                assertTrue(
                    elapsed < 15.seconds,
                    "the handle was not released within a bounded time: $elapsed",
                )
            } finally {
                remote.release()
                factory.closeOwningFactory()
            }
        }

    private companion object {
        const val CYCLE_FINISHED = "cycle-finished"
        const val HANDLE_CLOSED = "handle-closed"

        /** A non-sentinel owner, so the cycle is admitted and reaches the remote call. */
        const val SYNCING_OWNER = "owner-1"

        /**
         * The accepted residual window of `D-172`. Work that never observes cancellation would
         * otherwise hold the handle for the life of the process, so the release is bounded by this
         * deadline. The margin below it is generous on purpose: the assertion is that the release
         * happens at roughly the deadline while the cycle is still parked, not that it is exact.
         */
        const val BACKSTOP_MILLIS = 5_000L
    }
}

/**
 * A remote source that enters a `NonCancellable` section and stays there until released, so a cycle
 * can be held past the cancellation that `close()` performs. It reports that it entered, and it
 * reports in a `finally` when the section ends.
 */
private class NonCancellableRemote(
    private val onFinished: () -> Unit,
) : RemoteSyncSource {
    val entered = CompletableDeferred<Unit>()
    private val release = CompletableDeferred<Unit>()

    fun release() {
        if (!release.isCompleted) release.complete(Unit)
    }

    override suspend fun pushSnapshot(
        ownerId: OwnerId,
        snapshot: EntitySnapshot,
    ): Outcome<RemoteAck, RemoteError> =
        Outcome.Ok(
            RemoteAck(
                entityType = snapshot.entityType,
                entityId = snapshot.entityId,
                serverUpdatedAt = kotlin.time.Instant.fromEpochMilliseconds(0),
            ),
        )

    override suspend fun pullChanges(
        ownerId: OwnerId,
        entityType: EntityType,
        cursor: RemoteCursor,
        limit: Int,
    ): Outcome<RemotePage, RemoteError> {
        entered.complete(Unit)
        return withContext(NonCancellable) {
            try {
                release.await()
            } finally {
                onFinished()
            }
            Outcome.Ok(RemotePage(items = emptyList(), nextCursor = cursor, hasMore = false))
        }
    }
}

/**
 * Records the moment the handle is released, so it can be compared with the moment the cycle
 * finished. The owning in-memory factory is closed separately, after the assertions.
 */
private class OrderRecordingDatabaseFactory(
    private val events: MutableList<String>,
) : DatabaseFactory {
    private val owning = InMemoryDatabaseFactory()
    private val handle: DatabaseHandle = owning.create()

    var closeCalls: Int = 0
        private set

    override fun create(): DatabaseHandle =
        object : DatabaseHandle {
            override val database = handle.database

            override fun close() {
                if (closeCalls > 0) return
                closeCalls += 1
                events += "handle-closed"
            }
        }

    fun closeOwningFactory() = owning.close()
}
