package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.RemoteError
import com.ruizurraca.carapp.core.model.OwnerId
import com.ruizurraca.carapp.core.sync.EntitySnapshot
import com.ruizurraca.carapp.core.sync.EntityType
import com.ruizurraca.carapp.core.sync.RemoteAck
import com.ruizurraca.carapp.core.sync.RemoteCursor
import com.ruizurraca.carapp.core.sync.RemotePage
import com.ruizurraca.carapp.core.sync.RemoteSyncSource
import com.ruizurraca.carapp.core.testing.FakeConnectivityObserver
import com.ruizurraca.carapp.core.testing.FakeOwnerContext
import com.ruizurraca.carapp.core.testing.RecordingSyncTriggerAdapter
import com.ruizurraca.carapp.shared.testing.testAppGraphDependencies
import com.ruizurraca.carapp.shared.testing.testAppProviders
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The graph-level trigger wiring of `E3-04`.
 *
 * `docs/CONTRACTS.md §9.8` names six triggers. Four are fired elsewhere - `PostWriteDebounce` by the
 * commit paths, `PullToRefresh` by a user intent, `Periodic` by the platform scheduler through
 * `SyncTriggerAdapter`, and `OwnerChanged` by the owner-recovery coordinator when a non-sentinel
 * owner transition resolves. The remaining two depend on the graph because it owns their source:
 * `ConnectivityRecovered`, derived from the `ConnectivityObserver` edge, and `AppForeground` through
 * the holder's threshold, which the host feeds with its measured background time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppGraphTriggerWiringTest {
    @Test
    fun anOfflineToOnlineEdgeTriggersExactlyOneCycle() =
        runTest {
            val connectivity = FakeConnectivityObserver(initiallyOnline = false)
            val remote = CountingRemote()
            val graph = graphOver(connectivity, remote)

            try {
                // Offline: no cycle may be admitted, so the transport must stay untouched (§9.2).
                connectivity.set(false)
                advanceGraphWork()
                assertEquals(
                    emptyList(),
                    remote.pulls,
                    "no cycle may run while the device is offline, so no pull may reach the transport",
                )

                connectivity.set(true)
                awaitCondition("the recovery cycle to reach the transport") { remote.pulls.size == 2 }

                assertEquals(
                    listOf(EntityType.VEHICLE, EntityType.FUEL_ENTRY),
                    remote.pulls,
                    "one recovery cycle pulls both entity types, in dependency order",
                )
            } finally {
                graph.close()
            }
        }

    @Test
    fun anUnchangedOnlineSignalIsNotARecoveryTrigger() =
        runTest {
            // The trigger is the transition, not the value: a device that never went offline gained no
            // recovery, so re-publishing the same `true` must not start a cycle.
            val connectivity = FakeConnectivityObserver(initiallyOnline = true)
            val remote = CountingRemote()
            val graph = graphOver(connectivity, remote)

            try {
                // The device is online and was observed online, so its state never recovered: a later
                // redundant `true` is a transition into the state it already had, not out of offline.
                connectivity.set(true)
                advanceGraphWork()

                assertEquals(
                    emptyList(),
                    remote.pulls,
                    "an unchanged connectivity value MUST NOT be treated as a recovery",
                )
            } finally {
                graph.close()
            }
        }

    @Test
    fun everyOfflineToOnlineEdgeTriggersItsOwnCycle() =
        runTest {
            // Each recovery is a fresh edge, so each one is served by its own cycle. This is the
            // difference between deriving the trigger from a transition and polling the value: a
            // device that keeps dropping and regaining connectivity keeps backing up.
            val connectivity = FakeConnectivityObserver(initiallyOnline = false)
            val remote = CountingRemote()
            val graph = graphOver(connectivity, remote)

            try {
                connectivity.set(true)
                awaitCondition("the first recovery cycle") { remote.pulls.size == 2 }

                connectivity.set(false)
                advanceGraphWork()
                connectivity.set(true)
                awaitCondition("the second recovery cycle") { remote.pulls.size == 4 }

                assertEquals(
                    listOf(
                        EntityType.VEHICLE,
                        EntityType.FUEL_ENTRY,
                        EntityType.VEHICLE,
                        EntityType.FUEL_ENTRY,
                    ),
                    remote.pulls,
                    "two offline-to-online edges MUST produce two cycles",
                )
            } finally {
                graph.close()
            }
        }

    private fun kotlinx.coroutines.test.TestScope.graphOver(
        connectivity: FakeConnectivityObserver,
        remote: RemoteSyncSource,
        adapter: RecordingSyncTriggerAdapter = RecordingSyncTriggerAdapter(),
    ): AppGraph {
        // Confined to the caller's scheduler, so `advanceUntilIdle` drives every graph-owned task.
        // The default test dispatcher is unconfined, which would run the work eagerly on the test
        // thread and make the offline/online edges unobservable.
        val dependencies =
            confinedGraphDependencies(
                testAppGraphDependencies(
                    ownerContext = FakeOwnerContext(OwnerId("owner-1")),
                    connectivityObserver = connectivity,
                    remoteSyncSource = remote,
                    syncTriggerAdapter = adapter,
                ),
            )
        return buildAppGraph(true, testAppProviders(dependencies))
    }
}

/** Records the pull order a cycle produced, which is the observable of an admitted cycle. */
private class CountingRemote : RemoteSyncSource {
    val pulls = mutableListOf<EntityType>()

    override suspend fun pushSnapshot(
        ownerId: OwnerId,
        snapshot: EntitySnapshot,
    ): Outcome<RemoteAck, RemoteError> = Outcome.Err(RemoteError.Unknown)

    override suspend fun pullChanges(
        ownerId: OwnerId,
        entityType: EntityType,
        cursor: RemoteCursor,
        limit: Int,
    ): Outcome<RemotePage, RemoteError> {
        pulls += entityType
        return Outcome.Ok(RemotePage(items = emptyList(), nextCursor = cursor, hasMore = false))
    }
}
