package com.ruizurraca.carapp

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import com.ruizurraca.carapp.core.database.DatabaseFactory
import com.ruizurraca.carapp.core.database.DatabaseHandle
import com.ruizurraca.carapp.core.model.LOCAL_OWNER
import com.ruizurraca.carapp.core.model.OwnerId
import com.ruizurraca.carapp.core.testing.FakeConnectivityObserver
import com.ruizurraca.carapp.core.testing.FakeOwnerContext
import com.ruizurraca.carapp.core.testing.InMemoryDatabaseFactory
import com.ruizurraca.carapp.shared.testing.testAppGraphDependencies
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

/**
 * `E4-03` (`D-254`, ADR-0255): the benchmark history is written through the graph's own repositories,
 * only while the device is offline and the owner is `LOCAL_OWNER`, so nothing is enqueued.
 */
class BenchmarkSeedingTest {
    @Test
    fun theFirstPlannedEntryCarriesTheDocumentedValues() {
        assertEquals(
            BenchmarkFuelEntrySeed(
                dateEpochMillis = 1_577_836_800_000L,
                odometerKm = 10_500L,
                litersScaled = 40_000L,
                pricePerLiterScaled = 1_500L,
                isFullTank = true,
            ),
            benchmarkFuelEntrySeed(0),
        )
    }

    @Test
    fun everyTenthPlannedEntryIsAPartialFill() {
        assertFalse(benchmarkFuelEntrySeed(9).isFullTank)
    }

    @Test
    fun seedingWritesOneVehicleAndExactlyTheRequestedFuelEntries() =
        runTest {
            withSeedingGraph { graph, handle ->
                assertIs<BenchmarkSeedResult.Seeded>(seedBenchmarkFuelHistory(graph, SEEDED_ENTRIES))

                val queries = handle.database.databaseQueries
                assertEquals(
                    1L,
                    queries.countActiveVehiclesByOwner(LOCAL_OWNER.value).awaitAsOne(),
                )
                assertEquals(
                    SEEDED_ENTRIES.toLong(),
                    queries.countActiveFuelEntriesByOwner(LOCAL_OWNER.value).awaitAsOne(),
                )
            }
        }

    @Test
    fun seedingWhileTheOwnerIsLocalEnqueuesNothing() =
        runTest {
            withSeedingGraph { graph, handle ->
                assertIs<BenchmarkSeedResult.Seeded>(seedBenchmarkFuelHistory(graph, SEEDED_ENTRIES))

                assertEquals(
                    0L,
                    handle.database.databaseQueries
                        .countOutboxRows()
                        .awaitAsOne(),
                )
            }
        }

    @Test
    fun seedingTwiceFromAnEmptyDatabaseWritesIdenticalHistories() =
        runTest {
            val first = seededHistory()
            val second = seededHistory()

            assertEquals(SEEDED_ENTRIES, first.size, "precondition: the seeding wrote the history")
            assertEquals(first, second)
        }

    @Test
    fun seedingRefusesWhileTheDeviceIsOnline() =
        runTest {
            withSeedingGraph(online = true) { graph, handle ->
                assertEquals(
                    BenchmarkSeedResult.Refused(BenchmarkSeedRefusal.ONLINE),
                    seedBenchmarkFuelHistory(graph, SEEDED_ENTRIES),
                )
                assertEquals(
                    0L,
                    handle.database.databaseQueries
                        .countActiveVehiclesByOwner(LOCAL_OWNER.value)
                        .awaitAsOne(),
                )
            }
        }

    @Test
    fun seedingRefusesForAnOwnerOtherThanTheLocalSentinel() =
        runTest {
            withSeedingGraph(owner = SIGNED_IN_OWNER) { graph, handle ->
                assertEquals(
                    BenchmarkSeedResult.Refused(BenchmarkSeedRefusal.NOT_LOCAL_OWNER),
                    seedBenchmarkFuelHistory(graph, SEEDED_ENTRIES),
                )
                assertEquals(
                    0L,
                    handle.database.databaseQueries
                        .countActiveVehiclesByOwner(SIGNED_IN_OWNER.value)
                        .awaitAsOne(),
                )
            }
        }

    private suspend fun TestScope.seededHistory(): List<String> {
        var history = emptyList<String>()
        withSeedingGraph { graph, handle ->
            val seeded = assertIs<BenchmarkSeedResult.Seeded>(seedBenchmarkFuelHistory(graph, SEEDED_ENTRIES))
            history =
                handle.database.databaseQueries
                    .selectActiveFuelEntriesByVehicle(seeded.vehicleId)
                    .awaitAsList()
                    .map { row ->
                        "${row.date}|${row.odometerKm}|${row.litersScaled}|" +
                            "${row.pricePerLiterScaled}|${row.totalCostMinor}|${row.isFullTank}"
                    }
        }
        return history
    }

    private suspend fun TestScope.withSeedingGraph(
        online: Boolean = false,
        owner: OwnerId = LOCAL_OWNER,
        block: suspend (AppGraph, DatabaseHandle) -> Unit,
    ) {
        val owningFactory = InMemoryDatabaseFactory()
        val handle = owningFactory.create()
        val dependencies =
            confinedGraphDependencies(
                testAppGraphDependencies(
                    databaseFactory = fixedDatabaseFactory(handle),
                    ownerContext = FakeOwnerContext(owner),
                    connectivityObserver = FakeConnectivityObserver(initiallyOnline = online),
                ),
            )
        val graph = DefaultAppGraph(dependencies)
        try {
            advanceGraphWork()
            block(graph, handle)
        } finally {
            graph.close()
            // Only the factory releases the handle (`E1-18`): closing it here would block this thread.
            owningFactory.close()
        }
    }

    private fun fixedDatabaseFactory(handle: DatabaseHandle): DatabaseFactory =
        object : DatabaseFactory {
            override fun create() = handle
        }

    private companion object {
        const val SEEDED_ENTRIES = 25
        val SIGNED_IN_OWNER = OwnerId("signed-in-owner")
    }
}
