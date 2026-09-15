package com.ruizurraca.carapp

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.OwnerContext
import com.ruizurraca.carapp.core.common.RemoteError
import com.ruizurraca.carapp.core.common.SyncStatus
import com.ruizurraca.carapp.core.common.SyncTrigger
import com.ruizurraca.carapp.core.crash.CrashReporter
import com.ruizurraca.carapp.core.database.DatabaseFactory
import com.ruizurraca.carapp.core.database.DatabaseHandle
import com.ruizurraca.carapp.core.model.LOCAL_OWNER
import com.ruizurraca.carapp.core.model.OwnerId
import com.ruizurraca.carapp.core.sync.EntitySnapshot
import com.ruizurraca.carapp.core.sync.EntityType
import com.ruizurraca.carapp.core.sync.RemoteAck
import com.ruizurraca.carapp.core.sync.RemoteCursor
import com.ruizurraca.carapp.core.sync.RemotePage
import com.ruizurraca.carapp.core.sync.RemoteSyncSource
import com.ruizurraca.carapp.core.testing.FakeConnectivityObserver
import com.ruizurraca.carapp.shared.testing.testAppProviders
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class VehicleFormStateHolderTest {
    @Test
    fun savePersistsACompletePendingVehicleForTheCurrentOwner() =
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
                                ownerContext = fixedOwnerContext(OwnerId("anonymous-user")),
                                connectivityObserver = FakeConnectivityObserver(initiallyOnline = false),
                            ),
                        ),
                )
            val harness = AppGraphTestHarness(graph, backgroundScope)

            try {
                val holder = graph.vehicleFormStateHolder(harness.scope, vehicleId = null)
                holder.setName("Roadster")

                holder.save()
                holder.state.awaitState("vehicle creation finished") { state ->
                    state.savedVehicleId != null && !state.isSaving
                }

                val vehicle =
                    database.databaseQueries
                        .selectVehicleById("00000000-0000-4000-8000-000000000001")
                        .awaitAsOneOrNull()
                assertNotNull(vehicle)
                assertEquals("anonymous-user", vehicle.ownerId)
                assertEquals("Roadster", vehicle.name)
                assertEquals("roadster", vehicle.nameFold)
                assertEquals(0L, vehicle.initialOdometerKm)
                assertEquals(0L, vehicle.currentOdometerKm)
                assertEquals("GASOLINE", vehicle.fuelType)
                assertEquals("PENDING", vehicle.syncState)
                assertEquals(1L, vehicle.localRevision)
                assertEquals(2L, vehicle.localMutationSeq)
                assertEquals(1L, vehicle.schemaVersion)
                assertEquals(0L, vehicle.deleted)
                assertEquals(null, vehicle.deletedAt)
            } finally {
                harness.close()
            }
        }

    @Test
    fun saveEnqueuesTheClosedRemoteVehicleSnapshot() =
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
                                ownerContext = fixedOwnerContext(OwnerId("anonymous-user")),
                                connectivityObserver = FakeConnectivityObserver(initiallyOnline = false),
                            ),
                        ),
                )
            val harness = AppGraphTestHarness(graph, backgroundScope)

            try {
                val holder = graph.vehicleFormStateHolder(harness.scope, vehicleId = null)
                holder.setName("Roadster")

                holder.save()
                holder.state.awaitState("vehicle outbox snapshot saved") { state ->
                    state.savedVehicleId != null && !state.isSaving
                }

                val outbox =
                    database.databaseQueries
                        .selectOutboxByEntity(
                            entityType = "VEHICLE",
                            entityId = "00000000-0000-4000-8000-000000000001",
                        ).awaitAsOneOrNull()
                assertNotNull(outbox)
                assertEquals(1L, outbox.localRevision)
                assertEquals(0L, outbox.attemptCount)
                assertEquals(0L, outbox.nextAttemptAt)
                assertEquals(
                    setOf(
                        "entityType",
                        "id",
                        "ownerId",
                        "name",
                        "initialOdometerKm",
                        "brand",
                        "model",
                        "fuelType",
                        "createdAt",
                        "updatedAt",
                        "deleted",
                        "deletedAt",
                        "schemaVersion",
                    ),
                    Json.parseToJsonElement(outbox.payload).jsonObject.keys,
                )
            } finally {
                harness.close()
            }
        }

    @Test
    fun savePushesTheSnapshotOnlyAfterTheLocalTransactionCommits() =
        runTest {
            val crashes = mutableListOf<AppError>()
            val defaultDependencies = confinedGraphDependencies()
            val databaseHandle = defaultDependencies.databaseFactory.create()
            val database = databaseHandle.database
            val remote =
                RecordingRemoteSyncSource { ownerId, snapshot ->
                    val localVehicle =
                        database.databaseQueries
                            .selectVehicleById(snapshot.entityId.value)
                            .awaitAsOneOrNull()
                    assertNotNull(localVehicle, "The local transaction must commit before remote push")
                    assertEquals(ownerId.value, localVehicle.ownerId)
                }
            val graph =
                buildAppGraph(
                    isDebugBuild = true,
                    providers =
                        testAppProviders(
                            defaultDependencies.copy(
                                databaseFactory = fixedDatabaseFactory(databaseHandle),
                                ownerContext = fixedOwnerContext(OwnerId("anonymous-user")),
                                remoteSyncSource = remote,
                                crashReporter =
                                    object : CrashReporter {
                                        override fun recordNonFatal(
                                            error: AppError,
                                            fields: Map<String, String>,
                                        ) {
                                            crashes += error
                                        }

                                        override fun setEnabled(enabled: Boolean) = Unit
                                    },
                            ),
                        ),
                )
            val harness = AppGraphTestHarness(graph, backgroundScope)

            try {
                val holder = graph.vehicleFormStateHolder(harness.scope, vehicleId = null)
                holder.setName("Roadster")

                holder.save()
                holder.state.awaitState("vehicle local commit finished") { state ->
                    state.savedVehicleId != null && !state.isSaving
                }
                graph.syncController().requestSync(SyncTrigger.PostWriteDebounce)
                graph.syncController().status.awaitState("vehicle pushed") { remote.pushCalls.isNotEmpty() }
                assertEquals(emptyList(), crashes)
                val call = remote.pushCalls.single()
                assertEquals("anonymous-user", call.first.value)
                assertEquals(EntityType.VEHICLE, call.second.entityType)
                assertEquals("00000000-0000-4000-8000-000000000001", call.second.entityId.value)
                assertEquals(1, call.second.schemaVersion)
            } finally {
                harness.close()
            }
        }

    @Test
    fun visibleRemotePushDoesNotMeanThePostWriteSyncCycleHasSettled() =
        runTest {
            val releasePull = CompletableDeferred<Unit>()
            val defaultDependencies = confinedGraphDependencies()
            val databaseHandle = defaultDependencies.databaseFactory.create()
            val remote =
                RecordingRemoteSyncSource(
                    onPush = { _, _ -> },
                    onPull = { releasePull.await() },
                )
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
                val holder = graph.vehicleFormStateHolder(harness.scope, vehicleId = null)
                holder.setName("Roadster")
                holder.save()
                holder.state.awaitState("vehicle save completed before blocked pull") { state ->
                    state.savedVehicleId != null && !state.isSaving
                }
                graph.syncController().status.awaitState("remote push visible during active cycle") { status ->
                    remote.pushCalls.size == 1 && status is SyncStatus.Syncing
                }

                val settled =
                    async {
                        graph.awaitSyncCycleSettled(
                            expectation = "post-write sync cycle settled",
                            expectedRemoteEffect = { remote.pushCalls.size == 1 },
                        )
                    }
                runCurrent()

                assertTrue(
                    settled.isActive,
                    "observing the remote push must not let teardown proceed while pull is blocked",
                )

                releasePull.complete(Unit)
                settled.await()
            } finally {
                releasePull.complete(Unit)
                harness.close()
            }
        }

    @Test
    fun vehicleOutboxPayloadWithEntityTypeReachesRemoteSyncSourceAsAValidSnapshot() =
        runTest {
            val defaultDependencies = confinedGraphDependencies()
            val databaseHandle = defaultDependencies.databaseFactory.create()
            val remote =
                RecordingRemoteSyncSource { _, snapshot ->
                    val json = Json.parseToJsonElement(snapshot.json).jsonObject
                    assertEquals("VEHICLE", json.getValue("entityType").jsonPrimitive.content)
                    assertEquals(EntityType.VEHICLE, snapshot.entityType)
                }
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
                val holder = graph.vehicleFormStateHolder(harness.scope, vehicleId = null)
                holder.setName("Roadster")
                holder.save()
                holder.state.awaitState("vehicle outbox payload saved") { state ->
                    state.savedVehicleId != null && !state.isSaving
                }
                graph.syncController().requestSync(SyncTrigger.PostWriteDebounce)
                graph.syncController().status.awaitState("vehicle payload pushed") { remote.pushCalls.isNotEmpty() }

                val snapshot = remote.pushCalls.single().second
                val json = Json.parseToJsonElement(snapshot.json).jsonObject
                assertEquals("VEHICLE", json.getValue("entityType").jsonPrimitive.content)
                assertEquals(EntityType.VEHICLE, snapshot.entityType)
            } finally {
                harness.close()
            }
        }

    @Test
    fun successfulRemoteAckMarksTheVehicleSyncedAndClearsItsOutboxRow() =
        runTest {
            val defaultDependencies = confinedGraphDependencies()
            val databaseHandle = defaultDependencies.databaseFactory.create()
            val database = databaseHandle.database
            val remote = RecordingRemoteSyncSource { _, _ -> }
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
                val holder = graph.vehicleFormStateHolder(harness.scope, vehicleId = null)
                holder.setName("Roadster")

                holder.save()
                holder.state.awaitState("vehicle remote ack applied") { state ->
                    state.savedVehicleId != null && !state.isSaving
                }
                graph.syncController().requestSync(SyncTrigger.PostWriteDebounce)
                graph.syncController().status.awaitState("vehicle ack persisted") { remote.pushCalls.isNotEmpty() }

                val vehicle =
                    database.databaseQueries
                        .selectVehicleById("00000000-0000-4000-8000-000000000001")
                        .awaitAsOneOrNull()
                assertNotNull(vehicle)
                assertEquals("SYNCED", vehicle.syncState)
                assertEquals(1_767_225_600_000L, vehicle.serverUpdatedAt)
                assertEquals(
                    null,
                    database.databaseQueries
                        .selectOutboxByEntity(
                            entityType = "VEHICLE",
                            entityId = vehicle.id,
                        ).awaitAsOneOrNull(),
                )
            } finally {
                harness.close()
            }
        }

    @Test
    fun localOwnerSavePersistsPendingVehicleWithoutOutboxOrRemotePush() =
        runTest {
            val defaultDependencies = confinedGraphDependencies()
            val databaseHandle = defaultDependencies.databaseFactory.create()
            val database = databaseHandle.database
            val remote = RecordingRemoteSyncSource { _, _ -> }
            val graph =
                buildAppGraph(
                    isDebugBuild = true,
                    providers =
                        testAppProviders(
                            defaultDependencies.copy(
                                databaseFactory = fixedDatabaseFactory(databaseHandle),
                                ownerContext = fixedOwnerContext(LOCAL_OWNER),
                                remoteSyncSource = remote,
                            ),
                        ),
                )
            val harness = AppGraphTestHarness(graph, backgroundScope)

            try {
                val holder = graph.vehicleFormStateHolder(harness.scope, vehicleId = null)
                holder.setName("Offline Roadster")

                holder.save()
                holder.state.awaitState("local owner vehicle save finished") { state ->
                    state.savedVehicleId != null && !state.isSaving
                }

                val vehicle =
                    database.databaseQueries
                        .selectVehicleById("00000000-0000-4000-8000-000000000001")
                        .awaitAsOneOrNull()
                assertNotNull(vehicle)
                assertEquals(LOCAL_OWNER.value, vehicle.ownerId)
                assertEquals("PENDING", vehicle.syncState)
                assertEquals(
                    null,
                    database.databaseQueries
                        .selectOutboxByEntity(
                            entityType = "VEHICLE",
                            entityId = vehicle.id,
                        ).awaitAsOneOrNull(),
                )
                assertEquals(emptyList(), remote.pushCalls)
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

private class RecordingRemoteSyncSource(
    private val onPull: suspend () -> Unit = {},
    private val onPush: suspend (OwnerId, EntitySnapshot) -> Unit,
) : RemoteSyncSource {
    private val recordedPushCalls = mutableListOf<Pair<OwnerId, EntitySnapshot>>()
    val pushCalls: List<Pair<OwnerId, EntitySnapshot>> get() = recordedPushCalls.toList()

    override suspend fun pushSnapshot(
        ownerId: OwnerId,
        snapshot: EntitySnapshot,
    ): Outcome<RemoteAck, RemoteError> {
        onPush(ownerId, snapshot)
        recordedPushCalls += ownerId to snapshot
        return Outcome.Ok(
            RemoteAck(
                entityType = snapshot.entityType,
                entityId = snapshot.entityId,
                serverUpdatedAt = Instant.fromEpochMilliseconds(1_767_225_600_000L),
            ),
        )
    }

    override suspend fun pullChanges(
        ownerId: OwnerId,
        entityType: EntityType,
        cursor: RemoteCursor,
        limit: Int,
    ): Outcome<RemotePage, RemoteError> {
        onPull()
        return Outcome.Ok(RemotePage(emptyList(), cursor, hasMore = false))
    }
}
