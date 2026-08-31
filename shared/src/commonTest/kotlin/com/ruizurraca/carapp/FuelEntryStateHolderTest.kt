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
import com.ruizurraca.carapp.shared.testing.testAppGraphDependencies
import com.ruizurraca.carapp.shared.testing.testAppProviders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
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
                backgroundScope.launch { holder.state.collect() }
                advanceUntilIdle()

                val state = holder.state.value
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
                backgroundScope.launch { holder.state.collect() }
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
                backgroundScope.launch { holder.state.collect() }

                holder.setLitersScaled(45_123L)
                holder.setPricePerLiterScaled(1_789L)
                advanceUntilIdle()

                assertEquals(8_073L, holder.state.value.totalCostMinor)
                assertNull(holder.state.value.message)
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
                backgroundScope.launch { holder.state.collect() }

                holder.setLitersScaled(40_000L)
                holder.setPricePerLiterScaled(1_000_000L)
                advanceUntilIdle()

                assertEquals(40_000L, holder.state.value.litersScaled)
                assertEquals(1_000_000L, holder.state.value.pricePerLiterScaled)
                assertNull(holder.state.value.totalCostMinor)
                assertNull(holder.state.value.message)

                holder.save()
                advanceUntilIdle()

                assertEquals(
                    "VALIDATION.OUT_OF_RANGE",
                    holder.state.value.message
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
                backgroundScope.launch { list.state.collect() }
                backgroundScope.launch { form.state.collect() }

                form.setOdometerKm(50L)
                form.setLitersScaled(40_000L)
                form.setPricePerLiterScaled(1_500L)
                form.setFullTank(false)
                form.setMissedEntries(true)
                form.save()
                advanceUntilIdle()

                assertEquals(
                    "WARNING.ODOMETER_INCONSISTENT",
                    form.state.value.message
                        ?.code,
                )
                assertEquals(
                    Confirmation.OdometerInconsistent,
                    form.state.value.message
                        ?.confirmation,
                )
                assertTrue(
                    list.state.value.entries
                        .isEmpty(),
                )

                form.confirmSave(Confirmation.OdometerInconsistent)
                advanceUntilIdle()

                val row =
                    list.state.value.entries
                        .single()
                assertFalse(row.isFullTank)
                assertTrue(row.hasMissedEntries)
                assertTrue(row.odometerInconsistent)
                assertEquals(ConsumptionInvalidReason.EndEntryNotFullTank, row.invalidReason)
                assertEquals(SyncStatus.Idle, list.state.value.syncStatus)
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
                backgroundScope.launch { list.state.collect() }

                saveFullEntry(graph, backgroundScope, vehicleId, odometerKm = 100L)
                saveFullEntry(graph, backgroundScope, vehicleId, odometerKm = 500L)
                saveFullEntry(graph, backgroundScope, vehicleId, odometerKm = 900L)
                advanceUntilIdle()

                val state = list.state.value
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
                backgroundScope.launch { list.state.collect() }
                saveFullEntry(graph, backgroundScope, vehicleId, odometerKm = 100L)
                advanceUntilIdle()
                val entryId =
                    list.state.value.entries
                        .single()
                        .id

                list.requestDelete(entryId)
                advanceUntilIdle()

                assertEquals(
                    "INFO.CONFIRM_DELETE_FUEL_ENTRY",
                    list.state.value.message
                        ?.code,
                )
                assertEquals(1, list.state.value.entries.size)

                list.confirmDelete(entryId)
                advanceUntilIdle()

                assertTrue(
                    list.state.value.entries
                        .isEmpty(),
                )
            } finally {
                graph.close()
            }
        }

    private suspend fun createVehicle(
        graph: AppGraph,
        scope: CoroutineScope,
        initialOdometerKm: Long,
    ): String {
        val holder = graph.vehicleFormStateHolder(scope, vehicleId = null)
        scope.launch { holder.state.collect() }
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
        vehicleId: String,
        odometerKm: Long,
    ) {
        val holder = graph.fuelEntryFormStateHolder(scope, vehicleId, entryId = null)
        scope.launch { holder.state.collect() }
        holder.setOdometerKm(odometerKm)
        holder.setLitersScaled(40_000L)
        holder.setPricePerLiterScaled(1_500L)
        holder.save()
        advanceUntilIdle()
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
