package com.ruizurraca.carapp

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.OwnerContext
import com.ruizurraca.carapp.core.common.RemoteError
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
import com.ruizurraca.carapp.shared.testing.testAppGraphDependencies
import com.ruizurraca.carapp.shared.testing.testAppProviders
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Instant

class VehicleFormStateHolderTest {
    @Test
    fun savePersistsACompletePendingVehicleForTheCurrentOwner() =
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
                                ownerContext = fixedOwnerContext(OwnerId("anonymous-user")),
                            ),
                        ),
                )

            try {
                val holder = graph.vehicleFormStateHolder(backgroundScope, vehicleId = null)
                holder.setName("Roadster")

                holder.save()
                holder.state.first { state -> !state.isSaving }

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
                graph.close()
            }
        }

    @Test
    fun saveEnqueuesTheClosedRemoteVehicleSnapshot() =
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
                                ownerContext = fixedOwnerContext(OwnerId("anonymous-user")),
                            ),
                        ),
                )

            try {
                val holder = graph.vehicleFormStateHolder(backgroundScope, vehicleId = null)
                holder.setName("Roadster")

                holder.save()
                holder.state.first { state -> !state.isSaving }

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
                graph.close()
            }
        }

    @Test
    fun savePushesTheSnapshotOnlyAfterTheLocalTransactionCommits() =
        runTest {
            val defaultDependencies = testAppGraphDependencies()
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
                            ),
                        ),
                )

            try {
                val holder = graph.vehicleFormStateHolder(backgroundScope, vehicleId = null)
                holder.setName("Roadster")

                holder.save()
                holder.state.first { state -> !state.isSaving }

                val call = remote.pushCalls.single()
                assertEquals("anonymous-user", call.first.value)
                assertEquals(EntityType.VEHICLE, call.second.entityType)
                assertEquals("00000000-0000-4000-8000-000000000001", call.second.entityId.value)
                assertEquals(1, call.second.schemaVersion)
            } finally {
                graph.close()
            }
        }

    @Test
    fun successfulRemoteAckMarksTheVehicleSyncedAndClearsItsOutboxRow() =
        runTest {
            val defaultDependencies = testAppGraphDependencies()
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

            try {
                val holder = graph.vehicleFormStateHolder(backgroundScope, vehicleId = null)
                holder.setName("Roadster")

                holder.save()
                holder.state.first { state -> !state.isSaving }

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
                graph.close()
            }
        }

    @Test
    fun localOwnerSavePersistsPendingVehicleWithoutOutboxOrRemotePush() =
        runTest {
            val defaultDependencies = testAppGraphDependencies()
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

            try {
                val holder = graph.vehicleFormStateHolder(backgroundScope, vehicleId = null)
                holder.setName("Offline Roadster")

                holder.save()
                holder.state.first { state -> !state.isSaving }

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
                graph.close()
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
    ): Outcome<RemotePage, RemoteError> = Outcome.Ok(RemotePage(emptyList(), cursor, hasMore = false))
}
