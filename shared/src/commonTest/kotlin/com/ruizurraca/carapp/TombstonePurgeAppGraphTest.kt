package com.ruizurraca.carapp

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import com.ruizurraca.carapp.core.database.AppDatabase
import com.ruizurraca.carapp.core.database.DatabaseFactory
import com.ruizurraca.carapp.core.database.DatabaseHandle
import com.ruizurraca.carapp.core.testing.FakeAppClock
import com.ruizurraca.carapp.core.testing.InMemoryDatabaseFactory
import com.ruizurraca.carapp.shared.testing.testAppGraphDependencies
import com.ruizurraca.carapp.shared.testing.testAppProviders
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days

/**
 * `E3-07` criterion 2 through the product surface: the graph is the app start.
 *
 * `docs/CONTRACTS.md §8` requires the purge to run at most once per app start, so these tests assert
 * the graph's own startup work rather than a helper's idempotence. No sync trigger is fired: the graph
 * mounts under the `LOCAL_OWNER` sentinel, so `§9.1` admits no cycle for it and the purge is the only
 * local mutation that can remove a row.
 *
 * The clock is the graph's injected `AppClock`, so the 90-day cutoff is measured against the instant
 * the graph sees and no wall clock is read. The driver does its work on a real executor, so the waits
 * yield in real time through [awaitCondition] after releasing the graph's queued work.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TombstonePurgeAppGraphTest {
    @Test
    fun theGraphPurgesAConfirmedTombstoneAtStartup() =
        runTest {
            val handle = InMemoryDatabaseFactory().create()
            val clock = FakeAppClock()
            try {
                handle.database.seedVehicleTombstone(
                    id = CONFIRMED_ID,
                    serverUpdatedAt = clock.now().toEpochMilliseconds() - NINETY_ONE_DAYS,
                )

                val graph = mountGraph(handle, clock)
                try {
                    awaitCondition("the graph's startup purge to delete the confirmed tombstone") {
                        advanceStartupWork()
                        handle.database.vehicleRow(CONFIRMED_ID) == null
                    }
                } finally {
                    graph.close()
                }
            } finally {
                handle.close()
            }
        }

    /**
     * The other half of criterion 2, on the same graph: a tombstone that becomes purgable *after* the
     * startup purge has run must survive until the next app start. The invariant is "once per app
     * start", not "once per row".
     */
    @Test
    fun theGraphDoesNotPurgeATombstoneThatBecomesPurgableLaterInTheSameAppStart() =
        runTest {
            val handle = InMemoryDatabaseFactory().create()
            val clock = FakeAppClock()
            try {
                handle.database.seedVehicleTombstone(
                    id = CONFIRMED_ID,
                    serverUpdatedAt = clock.now().toEpochMilliseconds() - NINETY_ONE_DAYS,
                )

                val graph = mountGraph(handle, clock)
                try {
                    awaitCondition("the startup purge to delete the confirmed tombstone") {
                        advanceStartupWork()
                        handle.database.vehicleRow(CONFIRMED_ID) == null
                    }

                    // Equally purgable by state and age, but it appeared after this app start's purge.
                    handle.database.seedVehicleTombstone(
                        id = LATE_ID,
                        serverUpdatedAt = clock.now().toEpochMilliseconds() - NINETY_ONE_DAYS,
                    )
                    advanceGraphWork()

                    assertNotNull(
                        handle.database.vehicleRow(LATE_ID),
                        "the purge runs at most once per app start, so a later tombstone survives it",
                    )
                } finally {
                    graph.close()
                }
            } finally {
                handle.close()
            }
        }

    /**
     * Releases the graph's queued startup work and lets the driver's real executor make progress.
     *
     * The advance is a fresh bounded span on each round rather than one `advanceUntilIdle()`: the graph
     * re-arms work in virtual time, so `advanceUntilIdle()` would never return and the test task would
     * be killed with no result (`GraphTestDependencies`).
     */
    private fun TestScope.advanceStartupWork() {
        advanceGraphWork()
        runCurrent()
    }

    private fun TestScope.mountGraph(
        handle: DatabaseHandle,
        clock: FakeAppClock,
    ): AppGraph =
        buildAppGraph(
            isDebugBuild = true,
            providers =
                testAppProviders(
                    confinedGraphDependencies(
                        testAppGraphDependencies(
                            // The graph must read the handle this test seeds. `InMemoryDatabaseFactory`
                            // returns a brand-new isolated database on every `create()`, so handing it
                            // the factory would give the graph a second, empty database.
                            databaseFactory = SingleHandleDatabaseFactory(handle),
                            clock = clock,
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
        const val CONFIRMED_ID = "vehicle-confirmed"
        const val LATE_ID = "vehicle-late"
        val NINETY_ONE_DAYS = 91.days.inWholeMilliseconds
    }
}

private suspend fun AppDatabase.seedVehicleTombstone(
    id: String,
    serverUpdatedAt: Long,
) {
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
        serverUpdatedAt = serverUpdatedAt,
        deleted = 1,
        deletedAt = 1,
        syncState = "SYNCED",
        localRevision = 1,
        localMutationSeq = 1,
        schemaVersion = 1,
    )
}

private suspend fun AppDatabase.vehicleRow(id: String) =
    databaseQueries.selectVehicleById(id).awaitAsOneOrNull()
