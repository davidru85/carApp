package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.common.Confirmation
import com.ruizurraca.carapp.core.common.LocaleInfo
import com.ruizurraca.carapp.core.common.SyncStatus
import com.ruizurraca.carapp.core.database.DatabaseFactory
import com.ruizurraca.carapp.core.database.DatabaseHandle
import com.ruizurraca.carapp.core.model.ConsumptionInvalidReason
import com.ruizurraca.carapp.core.model.CurrencyCode
import com.ruizurraca.carapp.core.testing.FakeAppClock
import com.ruizurraca.carapp.core.testing.FakeLocaleProvider
import com.ruizurraca.carapp.feature.fuel.presentation.FuelEntryListStateHolder
import com.ruizurraca.carapp.shared.testing.testAppGraphDependencies
import com.ruizurraca.carapp.shared.testing.testAppProviders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class FuelEntryStateHolderTest {
    @Test
    fun newFormUsesExactClockVehicleOdometerAndSupportedLocaleCurrency() =
        runTest {
            val now = Instant.parse("2026-08-31T06:42:19.123Z")
            val graph =
                buildFuelGraph(
                    clock = FakeAppClock(now),
                    localeProvider = FakeLocaleProvider(LocaleInfo("en-US", "US", CurrencyCode("USD"))),
                )

            try {
                val vehicleId = createVehicle(graph, backgroundScope, initialOdometerKm = 12_345L)
                val holder = graph.fuelEntryFormStateHolder(backgroundScope, vehicleId, entryId = null)
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { holder.state.collect() }

                val state = holder.state.first { value -> value.odometerKm == 12_345L }
                assertEquals(now.toEpochMilliseconds(), state.dateEpochMillis)
                assertEquals(12_345L, state.odometerKm)
                assertEquals("USD", state.currencyCode)
                assertTrue(state.isFullTank)
                assertFalse(state.hasMissedEntries)
                assertNull(state.message)
            } finally {
                graph.close()
            }
        }

    @Test
    fun unsupportedLocaleCurrencyFallsBackToEur() =
        runTest {
            val graph =
                buildFuelGraph(
                    localeProvider = FakeLocaleProvider(LocaleInfo("ja-JP", "JP", CurrencyCode("JPY"))),
                )

            try {
                val vehicleId = createVehicle(graph, backgroundScope, initialOdometerKm = 1L)
                val holder = graph.fuelEntryFormStateHolder(backgroundScope, vehicleId, entryId = null)
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { holder.state.collect() }
                advanceUntilIdle()

                assertEquals("EUR", holder.state.value.currencyCode)
            } finally {
                graph.close()
            }
        }

    @Test
    fun litersAndPriceDeriveTotalCostWhileTyping() =
        runTest {
            val graph = buildFuelGraph()

            try {
                val vehicleId = createVehicle(graph, backgroundScope, initialOdometerKm = 100L)
                val holder = graph.fuelEntryFormStateHolder(backgroundScope, vehicleId, entryId = null)
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { holder.state.collect() }

                holder.setLitersScaled(45_123L)
                holder.setPricePerLiterScaled(1_789L)
                val derivedState = holder.state.first { state -> state.totalCostMinor == 8_073L }

                assertEquals(8_073L, derivedState.totalCostMinor)
                assertNull(derivedState.message)
            } finally {
                graph.close()
            }
        }

    @Test
    fun invalidLiveMoneyClearsDerivedValueAndWaitsUntilSaveToPublishError() =
        runTest {
            val graph = buildFuelGraph()

            try {
                val vehicleId = createVehicle(graph, backgroundScope, initialOdometerKm = 100L)
                val holder = graph.fuelEntryFormStateHolder(backgroundScope, vehicleId, entryId = null)
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { holder.state.collect() }

                holder.setLitersScaled(40_000L)
                holder.setPricePerLiterScaled(1_000_000L)
                val liveState =
                    holder.state.first { state ->
                        state.litersScaled == 40_000L && state.pricePerLiterScaled == 1_000_000L
                    }

                assertEquals(40_000L, liveState.litersScaled)
                assertEquals(1_000_000L, liveState.pricePerLiterScaled)
                assertNull(liveState.totalCostMinor)
                assertNull(liveState.message)

                holder.save()
                val savedState = holder.state.first { state -> state.message != null }

                assertEquals(
                    "VALIDATION.OUT_OF_RANGE",
                    savedState.message
                        ?.code,
                )
            } finally {
                graph.close()
            }
        }

    @Test
    fun inconsistentPartialEntryRequiresConfirmationThenPublishesBothIndicators() =
        runTest {
            val graph = buildFuelGraph()

            try {
                val vehicleId = createVehicle(graph, backgroundScope, initialOdometerKm = 100L)
                val list = graph.fuelEntryListStateHolder(backgroundScope, vehicleId)
                val form = graph.fuelEntryFormStateHolder(backgroundScope, vehicleId, entryId = null)
                var saveCompletionCount = 0
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { list.state.collect() }
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { form.state.collect() }
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    form.observeSaveCompletions().collect { saveCompletionCount += 1 }
                }

                form.setOdometerKm(50L)
                form.setLitersScaled(40_000L)
                form.setPricePerLiterScaled(1_500L)
                form.setFullTank(false)
                form.setMissedEntries(true)
                form.save()
                val warningState = form.state.first { state -> state.message != null }

                assertEquals(
                    "WARNING.ODOMETER_INCONSISTENT",
                    warningState.message
                        ?.code,
                )
                assertEquals(
                    Confirmation.OdometerInconsistent,
                    warningState.message
                        ?.confirmation,
                )
                assertTrue(
                    list.state.value.entries
                        .isEmpty(),
                )

                form.confirmSave(Confirmation.OdometerInconsistent)
                val publishedState = list.state.first { state -> state.entries.isNotEmpty() }

                val row = publishedState.entries.single()
                assertFalse(row.isFullTank)
                assertTrue(row.hasMissedEntries)
                assertTrue(row.odometerInconsistent)
                assertEquals(ConsumptionInvalidReason.EndEntryNotFullTank, row.invalidReason)
                assertEquals(SyncStatus.Idle, publishedState.syncStatus)
                assertEquals(1, saveCompletionCount)
            } finally {
                graph.close()
            }
        }

    @Test
    fun listPublishesWeightedSummaryAndReliabilityAfterThreeFullTanks() =
        runTest {
            val graph = buildFuelGraph()

            try {
                val vehicleId = createVehicle(graph, backgroundScope, initialOdometerKm = 100L)
                val list = graph.fuelEntryListStateHolder(backgroundScope, vehicleId)
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { list.state.collect() }

                saveFullEntry(graph, backgroundScope, list, vehicleId, odometerKm = 100L, expectedCount = 1)
                saveFullEntry(graph, backgroundScope, list, vehicleId, odometerKm = 500L, expectedCount = 2)
                saveFullEntry(graph, backgroundScope, list, vehicleId, odometerKm = 900L, expectedCount = 3)

                val state = list.state.first { value -> value.entries.size == 3 }
                assertEquals(3, state.entries.size)
                assertEquals(1_000L, state.consumptionAverageScaled)
                assertEquals(2, state.validConsumptionSegmentCount)
                assertTrue(state.isConsumptionReliable)
                assertEquals(SyncStatus.Idle, state.syncStatus)
            } finally {
                graph.close()
            }
        }

    @Test
    fun deleteRequiresConfirmationAndRemovesTheEntryFromTheReactiveList() =
        runTest {
            val graph = buildFuelGraph()

            try {
                val vehicleId = createVehicle(graph, backgroundScope, initialOdometerKm = 100L)
                val list = graph.fuelEntryListStateHolder(backgroundScope, vehicleId)
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { list.state.collect() }
                saveFullEntry(graph, backgroundScope, list, vehicleId, odometerKm = 100L, expectedCount = 1)
                val populatedState = list.state.first { state -> state.entries.isNotEmpty() }
                val entryId = populatedState.entries.single().id

                list.requestDelete(entryId)
                val confirmationState = list.state.first { state -> state.message != null }

                assertEquals(
                    "INFO.CONFIRM_DELETE_FUEL_ENTRY",
                    confirmationState.message
                        ?.code,
                )
                assertEquals(1, confirmationState.entries.size)

                list.confirmDelete(entryId)
                val deletedState = list.state.first { state -> !state.isLoading && state.entries.isEmpty() }

                assertTrue(deletedState.entries.isEmpty())
            } finally {
                graph.close()
            }
        }

    private suspend fun TestScope.createVehicle(
        graph: AppGraph,
        scope: CoroutineScope,
        initialOdometerKm: Long,
    ): String {
        val holder = graph.vehicleFormStateHolder(scope, vehicleId = null)
        scope.launch(UnconfinedTestDispatcher(testScheduler)) { holder.state.collect() }
        holder.setName("Roadster")
        holder.setInitialOdometerKm(initialOdometerKm)
        holder.save()
        val state = holder.state.first { value -> value.savedVehicleId != null && !value.isSaving }
        holder.close()
        return requireNotNull(state.savedVehicleId)
    }

    private suspend fun TestScope.saveFullEntry(
        graph: AppGraph,
        scope: CoroutineScope,
        list: FuelEntryListStateHolder,
        vehicleId: String,
        odometerKm: Long,
        expectedCount: Int,
    ) {
        val holder = graph.fuelEntryFormStateHolder(scope, vehicleId, entryId = null)
        scope.launch(UnconfinedTestDispatcher(testScheduler)) { holder.state.collect() }
        holder.setOdometerKm(odometerKm)
        holder.setLitersScaled(40_000L)
        holder.setPricePerLiterScaled(1_500L)
        holder.save()
        list.state.first { state -> state.entries.size == expectedCount }
        assertNull(holder.state.value.message)
        holder.close()
    }

    private fun buildFuelGraph(
        clock: FakeAppClock = FakeAppClock(),
        localeProvider: FakeLocaleProvider = FakeLocaleProvider(),
    ): AppGraph {
        val defaultDependencies =
            testAppGraphDependencies(
                clock = clock,
                localeProvider = localeProvider,
            )
        val databaseHandle = defaultDependencies.databaseFactory.create()
        return buildAppGraph(
            isDebugBuild = true,
            providers =
                testAppProviders(
                    defaultDependencies.copy(
                        databaseFactory = fixedDatabaseFactory(databaseHandle),
                    ),
                ),
        )
    }

    private fun fixedDatabaseFactory(databaseHandle: DatabaseHandle): DatabaseFactory =
        object : DatabaseFactory {
            override fun create() = databaseHandle
        }
}
