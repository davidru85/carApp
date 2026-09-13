package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.RemoteError
import com.ruizurraca.carapp.core.common.SyncStatus
import com.ruizurraca.carapp.core.common.SyncTrigger
import com.ruizurraca.carapp.core.model.OwnerId
import com.ruizurraca.carapp.core.sync.EntitySnapshot
import com.ruizurraca.carapp.core.sync.EntityType
import com.ruizurraca.carapp.core.sync.RemoteAck
import com.ruizurraca.carapp.core.sync.RemoteCursor
import com.ruizurraca.carapp.core.sync.RemotePage
import com.ruizurraca.carapp.core.sync.RemoteSyncSource
import com.ruizurraca.carapp.core.testing.FakeOwnerContext
import com.ruizurraca.carapp.feature.vehicle.presentation.VehicleFormStateHolder
import com.ruizurraca.carapp.feature.vehicle.presentation.VehicleListStateHolder
import com.ruizurraca.carapp.shared.testing.testAppGraphDependencies
import com.ruizurraca.carapp.shared.testing.testAppProviders
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AppGraphContractTest {
    @Test
    fun kotlinGraphReturnsRealVehicleHoldersWithCallerOwnedScopes() =
        runTest {
            val dependencies = testAppGraphDependencies()
            val graph: AppGraph =
                buildAppGraph(
                    isDebugBuild = true,
                    providers = testAppProviders(dependencies),
                )
            val harness = AppGraphTestHarness(graph, backgroundScope)

            try {
                assertIs<VehicleListStateHolder>(graph.vehicleListStateHolder(harness.scope))
                assertIs<VehicleFormStateHolder>(graph.vehicleFormStateHolder(harness.scope, null))
            } finally {
                harness.close()
            }
        }

    @Test
    fun vehicleAndFuelListsObserveTheSameGraphOwnedSyncStatus() =
        runTest {
            val releasePull = CompletableDeferred<Unit>()
            val remote = BlockingPullRemote(releasePull)
            val dependencies =
                testAppGraphDependencies(
                    ownerContext = FakeOwnerContext(OwnerId("owner-1")),
                    remoteSyncSource = remote,
                )
            val graph = buildAppGraph(true, testAppProviders(dependencies))
            val harness = AppGraphTestHarness(graph, backgroundScope)

            try {
                val vehicles = graph.vehicleListStateHolder(harness.scope)
                val fuelEntries = graph.fuelEntryListStateHolder(harness.scope, "vehicle-1")
                graph.syncController().requestSync(SyncTrigger.PullToRefresh)

                vehicles.state.awaitState("vehicle list observes syncing") { it.syncStatus == SyncStatus.Syncing }
                fuelEntries.state.awaitState("fuel list observes syncing") { it.syncStatus == SyncStatus.Syncing }
                assertEquals(vehicles.state.value.syncStatus, fuelEntries.state.value.syncStatus)

                releasePull.complete(Unit)
            } finally {
                releasePull.complete(Unit)
                harness.close()
            }
        }
}

private class BlockingPullRemote(
    private val release: CompletableDeferred<Unit>,
) : RemoteSyncSource {
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
        release.await()
        return Outcome.Ok(RemotePage(emptyList(), cursor, false))
    }
}
