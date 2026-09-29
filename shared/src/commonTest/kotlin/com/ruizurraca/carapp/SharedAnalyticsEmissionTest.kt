package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.analytics.AnalyticsEvent
import com.ruizurraca.carapp.core.analytics.AnalyticsUserProperties
import com.ruizurraca.carapp.core.analytics.CountBucket
import com.ruizurraca.carapp.core.common.AppError
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
    /**
     * The count source the fixture substitutes, kept in step by [vehicleCreated] and
     * [vehicleDeleted].
     *
     * That the counts describe *active* rows — and exclude tombstones — is
     * `OwnerActiveRowCountDatabaseAccessTest`'s contract, asserted there on the real SQL. These tests
     * assert the cadence and the bucketing: *when* `setUserProperties` is called and what it maps the
     * counts to.
     */
    private var activeVehicles = 0
    private var activeEntries = 0

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun aSuccessfulVehicleCreateEmitsTheEventAndRefreshesTheBuckets() =
        runTest {
            withGraph { graph, tracker ->

                createVehicle(graph, vehicleCommand())
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
                val vehicleId = createVehicle(graph, vehicleCommand()).value
                tracker.clear()

                createFuelEntry(graph, fuelEntryCommand(vehicleId))
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
                val vehicleId = createVehicle(graph, vehicleCommand()).value
                tracker.clear()

                val result = deleteVehicle(graph, vehicleId)

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
                val first = createVehicle(graph, vehicleCommand()).value
                createVehicle(graph, vehicleCommand(name = "Second"))
                deleteVehicle(graph, first)
                tracker.clear()

                createVehicle(graph, vehicleCommand(name = "Third"))

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
            // Deliberately not opted in: the point is that a disabled tracker records nothing even
            // though the write succeeds through the same chains.
            withGraph(optIn = false) { graph, tracker ->
                tracker.clear()

                assertIs<Outcome.Ok<EntityId>>(graph.vehicleRuntimeForTest().createVehicle(vehicleCommand()))

                assertEquals(emptyList(), tracker.events)
                assertEquals(emptyList(), tracker.userProperties)
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun TestScope.withGraph(
        optIn: Boolean = true,
        block: suspend (AppGraph, RecordingAnalyticsTracker) -> Unit,
    ) {
        activeVehicles = 0
        activeEntries = 0
        val tracker = RecordingAnalyticsTracker(initiallyEnabled = false)
        val dependencies =
            confinedGraphDependencies(
                testAppGraphDependencies(
                    databaseFactory = InMemoryDatabaseFactory(),
                    analyticsTracker = tracker,
                ),
            )
        val graph = DefaultAppGraph(dependencies)
        // The seams go in before the first drain, because the graph's collectors start in `init` and
        // an override installed later would be too late for the opt-in collector.
        if (optIn) graph.installAnalyticsTestSeams { activeVehicles to activeEntries }
        try {
            advanceUntilIdle()
            if (optIn) tracker.assertOptedIn()
            block(graph, tracker)
        } finally {
            graph.close()
        }
    }

    /**
     * The count source answers with the post-write state, so each helper updates it beside the write
     * it performs: the `§16.1` cadence refreshes the buckets *after* a successful write, which is the
     * value the emission must carry.
     */
    private suspend fun createVehicle(
        graph: AppGraph,
        command: CreateVehicleCommand,
    ): Outcome.Ok<EntityId> {
        // The cadence refreshes the buckets *after* a successful write, so the source must already
        // describe the post-write state when the write emits. A failure rolls the projection back.
        activeVehicles += 1
        val result = graph.vehicleRuntimeForTest().createVehicle(command)
        if (result !is Outcome.Ok) activeVehicles -= 1
        return assertIs(result, "the fixture must produce a successful create")
    }

    private suspend fun deleteVehicle(
        graph: AppGraph,
        id: EntityId,
    ): Outcome<Unit, AppError> {
        activeVehicles -= 1
        val result = graph.vehicleRuntimeForTest().repository.deleteVehicle(id)
        if (result !is Outcome.Ok) activeVehicles += 1
        return result
    }

    private suspend fun createFuelEntry(
        graph: AppGraph,
        command: CreateFuelEntryCommand,
    ): Outcome<EntityId, AppError> {
        activeEntries += 1
        val result = graph.fuelEntryRepositoryForTest().createFuelEntry(command)
        if (result !is Outcome.Ok) activeEntries -= 1
        return result
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
