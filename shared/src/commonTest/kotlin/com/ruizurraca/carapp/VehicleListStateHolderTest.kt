package com.ruizurraca.carapp

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.OwnerContext
import com.ruizurraca.carapp.core.common.RemoteError
import com.ruizurraca.carapp.core.database.AppDatabase
import com.ruizurraca.carapp.core.database.DatabaseFactory
import com.ruizurraca.carapp.core.database.DatabaseHandle
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.FuelType
import com.ruizurraca.carapp.core.model.LOCAL_OWNER
import com.ruizurraca.carapp.core.model.OwnerId
import com.ruizurraca.carapp.core.sync.EntitySnapshot
import com.ruizurraca.carapp.core.sync.EntityType
import com.ruizurraca.carapp.core.sync.RemoteAck
import com.ruizurraca.carapp.core.sync.RemoteCursor
import com.ruizurraca.carapp.core.sync.RemoteDocument
import com.ruizurraca.carapp.core.sync.RemotePage
import com.ruizurraca.carapp.core.sync.RemoteSyncSource
import com.ruizurraca.carapp.core.testing.FakeConnectivityObserver
import com.ruizurraca.carapp.feature.vehicle.presentation.VehicleListItemUi
import com.ruizurraca.carapp.shared.testing.testAppProviders
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

class VehicleListStateHolderTest {
    @Test
    fun listPublishesVehiclesPersistedThroughTheSharedForm() =
        runTest {
            val defaultDependencies = confinedGraphDependencies()
            val databaseHandle = defaultDependencies.databaseFactory.create()
            val database = databaseHandle.database
            val graph =
                buildAppGraph(
                    isDebugBuild = true,
                    providers =
                        testAppProviders(
                            defaultDependencies.copy(
                                databaseFactory = fixedDatabaseFactory(databaseHandle),
                                ownerContext = fixedOwnerContext(LOCAL_OWNER),
                            ),
                        ),
                )
            val harness = AppGraphTestHarness(graph, backgroundScope)

            try {
                val list = graph.vehicleListStateHolder(harness.scope)
                val form = graph.vehicleFormStateHolder(harness.scope, vehicleId = null)
                form.setName("Roadster")

                form.save()
                form.state.awaitState("vehicle save finished") { state ->
                    state.savedVehicleId != null && !state.isSaving
                }
                val publishedState =
                    list.state.awaitState("saved vehicle listed") { state -> state.vehicles.isNotEmpty() }

                assertEquals(
                    listOf(
                        VehicleListItemUi(
                            id = "00000000-0000-4000-8000-000000000001",
                            name = "Roadster",
                            currentOdometerKm = 0L,
                            fuelType = FuelType.GASOLINE,
                            deleted = false,
                        ),
                    ),
                    publishedState.vehicles,
                )
            } finally {
                harness.close()
            }
        }

    @Test
    fun refreshRestoresRemoteVehicleIntoEmptyLocalDatabaseForTheSameOwner() =
        runTest {
            val defaultDependencies = confinedGraphDependencies()
            val databaseHandle = defaultDependencies.databaseFactory.create()
            val database = databaseHandle.database
            val remote = PullOnlyRemoteSyncSource(remoteVehicleSnapshot())
            val graph =
                buildAppGraph(
                    isDebugBuild = true,
                    providers =
                        testAppProviders(
                            defaultDependencies.copy(
                                databaseFactory = fixedDatabaseFactory(databaseHandle),
                                ownerContext = fixedOwnerContext(OwnerId("anonymous-user")),
                                remoteSyncSource = remote,
                            ),
                        ),
                )
            val harness = AppGraphTestHarness(graph, backgroundScope)

            try {
                val list = graph.vehicleListStateHolder(harness.scope)

                list.refresh()
                list.state.awaitState("vehicle recovery finished") { state ->
                    !state.isLoading && state.vehicles.isNotEmpty()
                }
                graph.awaitSyncCycleSettled(
                    expectation = "vehicle recovery sync cycle settled",
                    expectedRemoteEffect = { remote.pullCalls.size == 2 },
                    expectedPersistedState = { database.hasSyncedVehicle(VEHICLE_ID) },
                )

                assertRecoveredVehicle(database)
                val publishedState =
                    list.state.awaitState("recovered vehicle listed") { state -> state.vehicles.isNotEmpty() }
                assertEquals(
                    "Recovered Roadster",
                    publishedState.vehicles.single().name,
                )
                assertRecoveryPullOrder(remote)
            } finally {
                harness.close()
            }
        }

    @Test
    fun failedRefreshPublishesTheErrorAndLetsTheNextRefreshRun() =
        runTest {
            val defaultDependencies = confinedGraphDependencies()
            val databaseHandle = defaultDependencies.databaseFactory.create()
            val remote = FailingPullRemoteSyncSource()
            val graph =
                buildAppGraph(
                    isDebugBuild = true,
                    providers =
                        testAppProviders(
                            defaultDependencies.copy(
                                databaseFactory = fixedDatabaseFactory(databaseHandle),
                                ownerContext = fixedOwnerContext(OwnerId("anonymous-user")),
                                remoteSyncSource = remote,
                            ),
                        ),
                )
            val harness = AppGraphTestHarness(graph, backgroundScope)

            try {
                val list = graph.vehicleListStateHolder(harness.scope)

                list.refresh()
                val failed = list.state.awaitState("refresh failure published") { state -> state.message != null }
                assertEquals("REMOTE.UNAVAILABLE", failed.message?.code)

                // The indicator cleared: a second refresh is not refused and reaches the remote again.
                list.refresh()
                graph.awaitSyncCycleSettled(
                    expectation = "second failed refresh cycle settled",
                    expectedRemoteEffect = { remote.pullCalls.size >= 2 },
                )
            } finally {
                harness.close()
            }
        }

    @Test
    fun offlineRefreshIsOkWithNoMessage() =
        runTest {
            val defaultDependencies = confinedGraphDependencies()
            val databaseHandle = defaultDependencies.databaseFactory.create()
            val graph =
                buildAppGraph(
                    isDebugBuild = true,
                    providers =
                        testAppProviders(
                            defaultDependencies.copy(
                                databaseFactory = fixedDatabaseFactory(databaseHandle),
                                ownerContext = fixedOwnerContext(OwnerId("anonymous-user")),
                                connectivityObserver = FakeConnectivityObserver(initiallyOnline = false),
                            ),
                        ),
                )
            val harness = AppGraphTestHarness(graph, backgroundScope)

            try {
                val list = graph.vehicleListStateHolder(harness.scope)

                list.refresh()
                val settled =
                    list.state.awaitState("offline refresh settles") { state ->
                        !state.isLoading && state.message == null
                    }
                assertEquals(emptyList(), settled.vehicles)
                assertNull(settled.message)
            } finally {
                harness.close()
            }
        }

    private fun fixedDatabaseFactory(databaseHandle: DatabaseHandle): DatabaseFactory =
        object : DatabaseFactory {
            override fun create() = databaseHandle
        }

    private fun fixedOwnerContext(ownerId: OwnerId): OwnerContext =
        object : OwnerContext {
            private val state = MutableStateFlow(ownerId)

            override val current: OwnerId = ownerId

            override fun observe(): Flow<OwnerId> = state
        }
}

private suspend fun AppDatabase.hasSyncedVehicle(vehicleId: String): Boolean =
    databaseQueries.selectVehicleById(vehicleId).awaitAsOneOrNull()?.syncState == "SYNCED"

private suspend fun assertRecoveredVehicle(database: AppDatabase) {
    val recovered = database.databaseQueries.selectVehicleById(VEHICLE_ID).awaitAsOneOrNull()
    assertNotNull(recovered)
    assertEquals("Recovered Roadster", recovered.name)
    assertEquals("SYNCED", recovered.syncState)
    assertEquals(1_767_225_600_000L, recovered.serverUpdatedAt)
    assertEquals(0L, recovered.localRevision)
    assertEquals(0L, recovered.localMutationSeq)
}

private fun assertRecoveryPullOrder(remote: PullOnlyRemoteSyncSource) {
    assertEquals(
        PullCall(
            ownerId = OwnerId("anonymous-user"),
            entityType = EntityType.VEHICLE,
            cursor = RemoteCursor.INITIAL,
            limit = 200,
        ),
        remote.pullCalls.first(),
    )
    assertEquals(
        listOf(EntityType.VEHICLE, EntityType.FUEL_ENTRY),
        remote.pullCalls.map(PullCall::entityType),
    )
}

private const val VEHICLE_ID = "00000000-0000-4000-8000-000000000001"

private fun remoteVehicleSnapshot(): RemoteDocument =
    RemoteDocument(
        entityType = EntityType.VEHICLE,
        documentId = EntityId("00000000-0000-4000-8000-000000000001"),
        serverUpdatedAt = Instant.fromEpochMilliseconds(1_767_225_600_000L),
        rawJson =
            """
            {
              "id":"00000000-0000-4000-8000-000000000001",
              "ownerId":"anonymous-user",
              "name":"Recovered Roadster",
              "initialOdometerKm":0,
              "brand":null,
              "model":null,
              "fuelType":"GASOLINE",
              "createdAt":1767225600000,
              "updatedAt":1767225600000,
              "deleted":false,
              "deletedAt":null,
              "schemaVersion":1
            }
            """.trimIndent(),
    )

private data class PullCall(
    val ownerId: OwnerId,
    val entityType: EntityType,
    val cursor: RemoteCursor,
    val limit: Int,
)

private class PullOnlyRemoteSyncSource(
    private val snapshot: RemoteDocument,
) : RemoteSyncSource {
    private val recordedPullCalls = mutableListOf<PullCall>()
    val pullCalls: List<PullCall> get() = recordedPullCalls.toList()

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
        recordedPullCalls += PullCall(ownerId, entityType, cursor, limit)
        return Outcome.Ok(
            RemotePage(
                items = listOf(snapshot),
                nextCursor =
                    RemoteCursor(
                        lastServerUpdatedAt = snapshot.serverUpdatedAt,
                        lastDocumentId = snapshot.documentId,
                    ),
                hasMore = false,
            ),
        )
    }
}

private class FailingPullRemoteSyncSource : RemoteSyncSource {
    private val recordedPullCalls = mutableListOf<PullCall>()
    val pullCalls: List<PullCall> get() = recordedPullCalls.toList()

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
        recordedPullCalls += PullCall(ownerId, entityType, cursor, limit)
        return Outcome.Err(RemoteError.Unavailable)
    }
}
