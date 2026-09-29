package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.analytics.AnalyticsEvent
import com.ruizurraca.carapp.core.analytics.AnalyticsUserProperties
import com.ruizurraca.carapp.core.analytics.CountBucket
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.model.CurrencyCode
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.FuelType
import com.ruizurraca.carapp.core.testing.InMemoryDatabaseFactory
import com.ruizurraca.carapp.core.testing.RecordingAnalyticsTracker
import com.ruizurraca.carapp.feature.fuel.domain.CreateFuelEntryCommand
import com.ruizurraca.carapp.feature.fuel.domain.FuelEntryRepository
import com.ruizurraca.carapp.feature.fuel.domain.MoneyInput
import com.ruizurraca.carapp.feature.session.domain.UpdateSettingsCommand
import com.ruizurraca.carapp.feature.vehicle.domain.CreateVehicleCommand
import com.ruizurraca.carapp.shared.testing.testAppGraphDependencies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlin.time.TimeSource

/**
 * `E3-09` (`D-196`, ADR-0196): the `docs/CONTRACTS.md §16.1` emission surface for the two write
 * event leaves and the `setUserProperties` cadence.
 *
 * These tests drive the **real** graph over the in-memory database, because the cadence is a rule
 * about the orchestration the graph builds, not about an isolated helper. A decorator that wrapped a
 * repository correctly but was not placed in the graph would pass a unit test of the decorator and
 * still emit nothing in the app, which is the failure this shape is chosen to catch.
 */
class SharedAnalyticsEmissionTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun aSuccessfulVehicleCreateEmitsTheEventAndRefreshesTheBuckets() =
        runTest {
            withGraph { graph, tracker ->
                optIn(graph, tracker)

                val result = graph.vehicleRuntimeForTest().createVehicle(vehicleCommand())

                assertIs<Outcome.Ok<EntityId>>(result, "the fixture must produce a successful create")
                assertEquals(
                    listOf<AnalyticsEvent>(AnalyticsEvent.VehicleCreated),
                    tracker.events,
                    "a committed vehicle create is one §16.1 event",
                )
                assertEquals(
                    listOf(AnalyticsUserProperties(CountBucket.ONE, CountBucket.ZERO)),
                    tracker.userProperties,
                    "the cadence refreshes the buckets after a successful write, from the active rows",
                )
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun aFailedVehicleCreateEmitsNothingAndRefreshesNothing() =
        runTest {
            withGraph { graph, tracker ->
                optIn(graph, tracker)

                // A blank name is rejected before any write, so this returns Err without committing.
                val result = graph.vehicleRuntimeForTest().createVehicle(vehicleCommand(name = "  "))

                assertIs<Outcome.Err<*>>(result, "the fixture must produce a rejected create")
                assertEquals(emptyList(), tracker.events, "a rejected write is not a product event")
                assertEquals(emptyList(), tracker.userProperties, "a rejected write refreshes nothing")
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun aSuccessfulFuelEntryCreateCarriesOnlyTheTwoBooleans() =
        runTest {
            withGraph { graph, tracker ->
                optIn(graph, tracker)
                val vehicleId =
                    assertIs<Outcome.Ok<EntityId>>(
                        graph.vehicleRuntimeForTest().createVehicle(vehicleCommand()),
                    ).value
                tracker.clear()

                val result = graph.fuelEntryRepositoryForTest().createFuelEntry(fuelEntryCommand(vehicleId))

                assertIs<Outcome.Ok<EntityId>>(result, "the fixture must produce a successful create")
                assertEquals(
                    listOf<AnalyticsEvent>(AnalyticsEvent.FuelEntryCreated(isFullTank = true, hadNotes = true)),
                    tracker.events,
                    "the leaf carries the two bucket-level booleans and never the note text itself",
                )
                assertEquals(
                    listOf(AnalyticsUserProperties(CountBucket.ONE, CountBucket.ONE)),
                    tracker.userProperties,
                )
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun aSuccessfulVehicleDeleteRefreshesTheBucketsWithoutEmittingAnEvent() =
        runTest {
            withGraph { graph, tracker ->
                optIn(graph, tracker)
                val vehicleId =
                    assertIs<Outcome.Ok<EntityId>>(
                        graph.vehicleRuntimeForTest().createVehicle(vehicleCommand()),
                    ).value
                tracker.clear()

                val result = graph.vehicleRuntimeForTest().repository.deleteVehicle(vehicleId)

                assertIs<Outcome.Ok<Unit>>(result, "the fixture must produce a successful delete")
                assertEquals(
                    emptyList(),
                    tracker.events,
                    "the closed hierarchy declares no VehicleDeleted leaf, so a delete emits no event",
                )
                assertEquals(
                    listOf(AnalyticsUserProperties(CountBucket.ZERO, CountBucket.ZERO)),
                    tracker.userProperties,
                    "the cadence still refreshes the buckets after the successful delete",
                )
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun theBucketsIgnoreTombstonedRows() =
        runTest {
            withGraph { graph, tracker ->
                optIn(graph, tracker)
                val first =
                    assertIs<Outcome.Ok<EntityId>>(
                        graph.vehicleRuntimeForTest().createVehicle(vehicleCommand()),
                    ).value
                graph.vehicleRuntimeForTest().createVehicle(vehicleCommand(name = "Second"))
                graph.vehicleRuntimeForTest().repository.deleteVehicle(first)
                tracker.clear()

                graph.vehicleRuntimeForTest().createVehicle(vehicleCommand(name = "Third"))

                assertEquals(
                    listOf(AnalyticsUserProperties(CountBucket.TWO_TO_FIVE, CountBucket.ZERO)),
                    tracker.userProperties,
                    "two active vehicles remain: the tombstoned one is not in the list the bucket describes",
                )
            }
        }

    /**
     * A disabled tracker observes nothing, which is the `§16.1` opt-in rule seen from the write path
     * rather than from the tracker: the emission code runs and the tracker drops what it is given.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun aDisabledTrackerRecordsNothingEvenThoughWritesSucceed() =
        runTest {
            withGraph { graph, tracker ->
                tracker.clear()

                assertIs<Outcome.Ok<EntityId>>(graph.vehicleRuntimeForTest().createVehicle(vehicleCommand()))

                assertEquals(emptyList(), tracker.events)
                assertEquals(emptyList(), tracker.userProperties)
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun TestScope.withGraph(block: suspend (AppGraph, RecordingAnalyticsTracker) -> Unit) {
        val tracker = RecordingAnalyticsTracker(initiallyEnabled = false)
        val dependencies =
            confinedGraphDependencies(
                testAppGraphDependencies(
                    databaseFactory = InMemoryDatabaseFactory(),
                    analyticsTracker = tracker,
                ),
            )
        val graph = DefaultAppGraph(dependencies)
        try {
            advanceUntilIdle()
            block(graph, tracker)
        } finally {
            graph.close()
        }
    }

    /**
     * Opts the fixture in through the product path.
     *
     * Setting the flag on the tracker directly would be overwritten: the graph's own settings
     * bootstrap publishes the freshly created row with `analyticsEnabled = false`, which disables
     * collection exactly as a real first launch does. Opting in through the repository is therefore
     * the only way to reach the state a real user reaches, and it exercises the same gate.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun TestScope.optIn(
        graph: AppGraph,
        tracker: RecordingAnalyticsTracker,
    ) {
        val repository = (graph as DefaultAppGraph).settingsRepositoryForTest
        repository.updateSettings(UpdateSettingsCommand(currency = null, analyticsEnabled = true))
        // The database notifies its observers on a real dispatcher, so the test must genuinely wait
        // for the persisted value rather than assume a virtual scheduler drain covered it; only then
        // can the graph's confined collector be drained and the opt-in be observed.
        repository.settings.first { result -> result is Outcome.Ok && result.value.analyticsEnabled }
        // Wait for the opt-in's own emission, not only for the tracker's flag. The bucket count is a
        // database read, so its continuation runs on a real dispatcher that virtual time cannot
        // drain; `awaitReal` is the only way to observe it without inventing a second count source.
        awaitReal { tracker.isEnabled && tracker.userProperties.isNotEmpty() }
        assertTrue(tracker.isEnabled, "the fixture must reach the opted-in state through the product path")
        assertTrue(tracker.userProperties.isNotEmpty(), "the opt-in must have emitted its buckets")
        tracker.clear()
    }

    /**
     * Waits on real time for a condition a virtual scheduler cannot reach.
     *
     * A database read resumes on the driver's own dispatcher, so `advanceUntilIdle()` cannot observe
     * its continuation. The wait is bounded, so a condition that never becomes true fails the fixture
     * rather than hanging it.
     */
    private suspend fun awaitReal(
        timeoutMillis: Long = REAL_WAIT_MILLIS,
        condition: () -> Boolean,
    ) {
        withContext(Dispatchers.Default) {
            // `TimeSource.Monotonic`, not `System.nanoTime()`: the latter is JVM-only and this test
            // also compiles for Kotlin/Native.
            val start = TimeSource.Monotonic.markNow()
            while (!condition() && start.elapsedNow() < timeoutMillis.milliseconds) {
                delay(REAL_WAIT_STEP_MILLIS)
            }
        }
    }

    private companion object {
        const val REAL_WAIT_MILLIS = 10_000L
        const val REAL_WAIT_STEP_MILLIS = 5L
    }

    private fun vehicleCommand(name: String = "Roadster") =
        CreateVehicleCommand(
            name = name,
            initialOdometerKm = 1_000,
            brand = null,
            model = null,
            fuelType = FuelType.GASOLINE,
            confirmations = emptySet(),
        )

    private fun fuelEntryCommand(vehicleId: EntityId) =
        CreateFuelEntryCommand(
            vehicleId = vehicleId,
            date = Instant.fromEpochMilliseconds(1_700_000_000_000),
            odometerKm = 2_000,
            money = MoneyInput.LitersAndTotal(litersScaled = 40_000, totalCostMinor = 6_000),
            currency = CurrencyCode("EUR"),
            isFullTank = true,
            hasMissedEntries = false,
            notes = "motorway",
            confirmations = emptySet(),
        )
}

/** Narrows the graph for the story's tests without widening the Kotlin-facing surface. */
internal fun AppGraph.vehicleRuntimeForTest(): VehicleSliceRuntime = (this as DefaultAppGraph).vehicleRuntimeForTest

internal fun AppGraph.fuelEntryRepositoryForTest(): FuelEntryRepository =
    (this as DefaultAppGraph).fuelRepositoryForTest
