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
import com.ruizurraca.carapp.feature.vehicle.domain.CreateVehicleCommand
import com.ruizurraca.carapp.shared.testing.testAppGraphDependencies
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant

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
                tracker.setEnabled(true)
                tracker.clear()

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
                tracker.setEnabled(true)
                tracker.clear()

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
                tracker.setEnabled(true)
                val vehicleId = assertIs<Outcome.Ok<EntityId>>(graph.vehicleRuntimeForTest().createVehicle(vehicleCommand())).value
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
                tracker.setEnabled(true)
                val vehicleId = assertIs<Outcome.Ok<EntityId>>(graph.vehicleRuntimeForTest().createVehicle(vehicleCommand())).value
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
                tracker.setEnabled(true)
                val first = assertIs<Outcome.Ok<EntityId>>(graph.vehicleRuntimeForTest().createVehicle(vehicleCommand())).value
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
internal fun AppGraph.vehicleRuntimeForTest(): VehicleSliceRuntime =
    (this as DefaultAppGraph).vehicleRuntimeForTest

internal fun AppGraph.fuelEntryRepositoryForTest(): FuelEntryRepository =
    (this as DefaultAppGraph).fuelRepositoryForTest
