package com.ruizurraca.carapp

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.OwnerContext
import com.ruizurraca.carapp.core.common.RemoteError
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
import com.ruizurraca.carapp.core.sync.RemotePage
import com.ruizurraca.carapp.core.sync.RemoteSnapshot
import com.ruizurraca.carapp.core.sync.RemoteSyncSource
import com.ruizurraca.carapp.feature.vehicle.presentation.VehicleListItemUi
import com.ruizurraca.carapp.shared.testing.testAppGraphDependencies
import com.ruizurraca.carapp.shared.testing.testAppProviders
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Instant

class VehicleListStateHolderTest {
    @Test
    fun listPublishesVehiclesPersistedThroughTheSharedForm() =
        runTest {
            val defaultDependencies = testAppGraphDependencies()
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
                form.state.first { state -> !state.isSaving }
                val publishedState = list.state.first { state -> state.vehicles.isNotEmpty() }

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
            val defaultDependencies = testAppGraphDependencies()
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
                list.state.first { state -> !state.isLoading }

                val recovered =
                    database.databaseQueries
                        .selectVehicleById("00000000-0000-4000-8000-000000000001")
                        .awaitAsOneOrNull()
                assertNotNull(recovered)
                assertEquals("Recovered Roadster", recovered.name)
                assertEquals("SYNCED", recovered.syncState)
                assertEquals(1_767_225_600_000L, recovered.serverUpdatedAt)
                assertEquals(0L, recovered.localRevision)
                assertEquals(0L, recovered.localMutationSeq)
                val publishedState = list.state.first { state -> state.vehicles.isNotEmpty() }
                assertEquals(
                    "Recovered Roadster",
                    publishedState.vehicles.single().name,
                )
                assertEquals(
                    PullCall(
                        ownerId = OwnerId("anonymous-user"),
                        entityType = EntityType.VEHICLE,
                        cursor = RemoteCursor.INITIAL,
                        limit = 50,
                    ),
                    remote.pullCalls.single(),
                )
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

private fun remoteVehicleSnapshot(): RemoteSnapshot =
    RemoteSnapshot(
        entityType = EntityType.VEHICLE,
        entityId = EntityId("00000000-0000-4000-8000-000000000001"),
        schemaVersion = 1,
        serverUpdatedAt = Instant.fromEpochMilliseconds(1_767_225_600_000L),
        deleted = false,
        json =
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
    private val snapshot: RemoteSnapshot,
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
                        lastDocumentId = snapshot.entityId,
                    ),
                hasMore = false,
            ),
        )
    }
}
