package com.ruizurraca.carapp

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import com.ruizurraca.carapp.core.common.EntityType
import com.ruizurraca.carapp.core.database.AppDatabase
import com.ruizurraca.carapp.core.database.DatabaseFactory
import com.ruizurraca.carapp.core.database.DatabaseHandle
import com.ruizurraca.carapp.core.database.SyncDatabaseAccess
import com.ruizurraca.carapp.core.database.SyncDatabaseCounts
import com.ruizurraca.carapp.core.model.OwnerId
import com.ruizurraca.carapp.core.testing.FakeConnectivityObserver
import com.ruizurraca.carapp.core.testing.FakeOwnerContext
import com.ruizurraca.carapp.core.testing.InMemoryDatabaseFactory
import com.ruizurraca.carapp.shared.testing.testAppGraphDependencies
import com.ruizurraca.carapp.shared.testing.testAppProviders
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `E3-21` acceptance through the product surface: the graph is the app start (`D-207`).
 *
 * A previous process died between `markSyncing` and the push's resolution, leaving a vehicle `SYNCING`
 * whose outbox row records a non-connectivity failure from the attempt before. The graph mounts for a
 * real owner while offline, and the test then reports the cold start exactly as both hosts do on every
 * launch, `SyncStateHolder.onForegroundReturn(null)`, which requests the `AppForeground` cycle a launch
 * begins with. That cycle is refused for the network, but the reset precedes the refusal, so the row
 * still returns to `FAILED_RETRYABLE` and the outbox row, which drives the aggregate, is unchanged.
 * `OwnerRecoveryGate` deliberately runs no cycle for an owner already resolved when the graph is
 * built, so without the host's report this start would run no cycle at all.
 *
 * This is an acceptance test of the wiring, not a TDD test: `DefaultSyncControllerStaleSyncingResetTest`
 * and `StaleSyncingResetDatabaseAccessTest` drove the behaviour. Teardown follows the `E1-18` /
 * `D-190` idiom of `TombstonePurgeAppGraphTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StaleSyncingResetAppGraphTest {
    @Test
    fun theGraphResetsAStaleSyncingRowAtStartupEvenOffline() =
        runTest {
            val factory = InMemoryDatabaseFactory()
            try {
                val handle = factory.create()
                handle.database.seedStaleVehicleAfterAFailedAttempt(VEHICLE_ID)
                val access = SyncDatabaseAccess(handle.database)
                assertEquals("SYNCING", handle.database.vehicleSyncState(VEHICLE_ID))
                assertEquals(SyncDatabaseCounts(pending = 1, retryable = 0, poisoned = 0), access.counts())

                val graph = mountGraph(handle)
                val syncHolder = graph.syncStateHolder(backgroundScope)
                try {
                    syncHolder.onForegroundReturn(null)
                    awaitCondition("the startup reset to restore the stale row") {
                        advanceStartupWork()
                        handle.database.vehicleSyncState(VEHICLE_ID) == "FAILED_RETRYABLE"
                    }

                    assertEquals(
                        SyncDatabaseCounts(pending = 0, retryable = 1, poisoned = 0),
                        access.counts(),
                        "the outbox row is unchanged, so the aggregate now reports the failed attempt",
                    )
                    assertEquals(
                        listOf("outbox type=VEHICLE id=$VEHICLE_ID attempt=3 error=REMOTE.UNKNOWN cycle=cycle-old"),
                        access.debugLines().filter { it.startsWith("outbox ") }.map { it.withoutTimes() },
                        "the reset never touches the outbox retry context",
                    )
                } finally {
                    syncHolder.close()
                    graph.awaitClosed()
                }
            } finally {
                factory.close()
            }
        }

    private fun TestScope.advanceStartupWork() {
        advanceGraphWork()
        runCurrent()
    }

    private fun TestScope.mountGraph(handle: DatabaseHandle): AppGraph =
        buildAppGraph(
            isDebugBuild = true,
            providers =
                testAppProviders(
                    confinedGraphDependencies(
                        testAppGraphDependencies(
                            databaseFactory = SingleHandleDatabaseFactory(handle),
                            ownerContext = FakeOwnerContext(OwnerId(OWNER_ID)),
                            connectivityObserver = FakeConnectivityObserver(initiallyOnline = false),
                        ),
                    ),
                ),
        )

    private class SingleHandleDatabaseFactory(
        private val handle: DatabaseHandle,
    ) : DatabaseFactory {
        override fun create(): DatabaseHandle = handle
    }

    private companion object {
        const val OWNER_ID = "owner-1"
        const val VEHICLE_ID = "vehicle-stale"
    }
}

/** A vehicle whose previous attempt failed with a non-connectivity code and whose retry then died mid-push. */
private suspend fun AppDatabase.seedStaleVehicleAfterAFailedAttempt(id: String) {
    databaseQueries.insertVehicleRow(
        id = id,
        ownerId = "owner-1",
        name = "Roadster",
        nameFold = "roadster",
        initialOdometerKm = 0,
        currentOdometerKm = 0,
        brand = null,
        model = null,
        fuelType = "GASOLINE",
        createdAt = 1,
        updatedAt = 1,
        serverUpdatedAt = null,
        deleted = 0,
        deletedAt = null,
        syncState = "PENDING",
        localRevision = 1,
        localMutationSeq = 1,
        schemaVersion = 1,
    )
    databaseQueries.coalesceOutbox(EntityType.VEHICLE.name, id, "{\"deleted\":false}", 1)
    val access = SyncDatabaseAccess(this)
    // Far-future retry, so no cycle of this test could push it even if one were admitted.
    access.failPush(EntityType.VEHICLE.name, id, 1, 3, Long.MAX_VALUE / 2, "REMOTE.UNKNOWN", false, "cycle-old")
    access.markSyncing(EntityType.VEHICLE.name, id, 1)
}

private suspend fun AppDatabase.vehicleSyncState(id: String): String? =
    databaseQueries.selectVehicleById(id).awaitAsOneOrNull()?.syncState

/** Drops the sequence and the far-future retry instant, keeping the retry context the test pins. */
private fun String.withoutTimes(): String =
    split(" ").filterNot { it.startsWith("next=") || it.startsWith("seq=") }.joinToString(" ")
