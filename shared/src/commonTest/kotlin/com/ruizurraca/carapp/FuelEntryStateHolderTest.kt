package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.common.Confirmation
import com.ruizurraca.carapp.core.common.LocaleInfo
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.PersistenceError
import com.ruizurraca.carapp.core.common.SyncStatus
import com.ruizurraca.carapp.core.database.DatabaseFactory
import com.ruizurraca.carapp.core.database.DatabaseHandle
import com.ruizurraca.carapp.core.database.SettingsDatabaseAccess
import com.ruizurraca.carapp.core.database.SettingsDatabaseRow
import com.ruizurraca.carapp.core.model.ConsumptionInvalidReason
import com.ruizurraca.carapp.core.model.CurrencyCode
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.FuelType
import com.ruizurraca.carapp.core.model.OwnerId
import com.ruizurraca.carapp.core.model.Vehicle
import com.ruizurraca.carapp.core.testing.FakeAppClock
import com.ruizurraca.carapp.core.testing.FakeLocaleProvider
import com.ruizurraca.carapp.core.testing.TestDispatcherProvider
import com.ruizurraca.carapp.feature.fuel.presentation.FuelEntryListStateHolder
import com.ruizurraca.carapp.shared.testing.testAppGraphDependencies
import com.ruizurraca.carapp.shared.testing.testAppProviders
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
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
    fun graphBootstrapCreatesSettingsWithoutAConsumer() =
        runTest {
            val dependencies =
                testAppGraphDependencies(
                    dispatchers = TestDispatcherProvider(StandardTestDispatcher(testScheduler)),
                    localeProvider =
                        FakeLocaleProvider(LocaleInfo("en-US", "US", CurrencyCode("USD"))),
                )
            val databaseHandle = dependencies.databaseFactory.create()
            val harness =
                AppGraphTestHarness(
                    graph =
                        buildAppGraph(
                            isDebugBuild = true,
                            providers =
                                testAppProviders(
                                    dependencies.copy(databaseFactory = fixedDatabaseFactory(databaseHandle)),
                                ),
                        ),
                    parentScope = backgroundScope,
                )

            try {
                val persisted =
                    SettingsDatabaseAccess(databaseHandle.database)
                        .observeSettings()
                        .first { row -> row != null }

                assertEquals(
                    SettingsDatabaseRow("USD", "KM", "LITER", analyticsEnabled = false),
                    persisted,
                )
            } finally {
                harness.close()
            }
        }

    @Test
    fun odometerSuggestionsIgnoreRepositoryErrorsAndMissingVehicles() =
        runTest {
            val vehicle =
                Vehicle(
                    id = EntityId("00000000-0000-4000-8000-000000000091"),
                    ownerId = OwnerId("owner-a"),
                    name = "Roadster",
                    initialOdometerKm = 100L,
                    currentOdometerKm = 345L,
                    brand = null,
                    model = null,
                    fuelType = FuelType.GASOLINE,
                    createdAt = Instant.parse("2026-08-31T06:42:19.123Z"),
                    updatedAt = Instant.parse("2026-08-31T06:42:19.123Z"),
                    deletedAt = null,
                )

            val suggestions =
                flowOf(
                    Outcome.Ok(vehicle),
                    Outcome.Err(PersistenceError.DatabaseUnavailable),
                    Outcome.Ok(null),
                ).fuelEntryOdometerSuggestions().toList()

            assertEquals(listOf(345L), suggestions)
        }

    @Test
    fun newFormUsesExactClockVehicleOdometerAndSupportedLocaleCurrency() =
        runTest {
            val now = Instant.parse("2026-08-31T06:42:19.123Z")
            val harness =
                AppGraphTestHarness(
                    graph =
                        buildFuelGraph(
                            clock = FakeAppClock(now),
                            localeProvider = FakeLocaleProvider(LocaleInfo("en-US", "US", CurrencyCode("USD"))),
                        ),
                    parentScope = backgroundScope,
                )
            val graph = harness.graph

            try {
                val vehicleId = createVehicle(harness, initialOdometerKm = 12_345L)
                val holder = graph.fuelEntryFormStateHolder(harness.scope, vehicleId, entryId = null)
                harness.collect(holder.state)

                val state = holder.state.first { value -> value.odometerKm == 12_345L }
                assertEquals(now.toEpochMilliseconds(), state.dateEpochMillis)
                assertEquals(12_345L, state.odometerKm)
                assertEquals("USD", state.currencyCode)
                assertTrue(state.isFullTank)
                assertFalse(state.hasMissedEntries)
                assertNull(state.message)
            } finally {
                harness.close()
            }
        }

    @Test
    fun unsupportedLocaleCurrencyFallsBackToEur() =
        runTest {
            val harness =
                AppGraphTestHarness(
                    graph =
                        buildFuelGraph(
                            localeProvider = FakeLocaleProvider(LocaleInfo("ja-JP", "JP", CurrencyCode("JPY"))),
                        ),
                    parentScope = backgroundScope,
                )
            val graph = harness.graph

            try {
                val vehicleId = createVehicle(harness, initialOdometerKm = 1L)
                val holder = graph.fuelEntryFormStateHolder(harness.scope, vehicleId, entryId = null)
                harness.collect(holder.state)
                advanceUntilIdle()

                assertEquals("EUR", holder.state.value.currencyCode)
            } finally {
                harness.close()
            }
        }

    @Test
    fun persistedSettingsCurrencyOverridesTheCurrentLocaleForNewEntries() =
        runTest {
            val dependencies =
                testAppGraphDependencies(
                    localeProvider =
                        FakeLocaleProvider(LocaleInfo("en-US", "US", CurrencyCode("USD"))),
                )
            val databaseHandle = dependencies.databaseFactory.create()
            SettingsDatabaseAccess(databaseHandle.database).upsertSettings(
                SettingsDatabaseRow("GBP", "KM", "LITER", analyticsEnabled = false),
            )
            val harness =
                AppGraphTestHarness(
                    graph =
                        buildAppGraph(
                            isDebugBuild = true,
                            providers =
                                testAppProviders(
                                    dependencies.copy(databaseFactory = fixedDatabaseFactory(databaseHandle)),
                                ),
                        ),
                    parentScope = backgroundScope,
                )
            val graph = harness.graph
            val holder =
                graph.fuelEntryFormStateHolder(
                    harness.scope,
                    vehicleId = "00000000-0000-4000-8000-000000000099",
                    entryId = null,
                )
            harness.collect(holder.state)

            try {
                assertEquals("GBP", holder.state.first { it.currencyCode == "GBP" }.currencyCode)
            } finally {
                harness.close()
            }
        }

    @Test
    fun litersAndPriceDeriveTotalCostWhileTyping() =
        runTest {
            val harness = AppGraphTestHarness(buildFuelGraph(), backgroundScope)
            val graph = harness.graph

            try {
                val vehicleId = createVehicle(harness, initialOdometerKm = 100L)
                val holder = graph.fuelEntryFormStateHolder(harness.scope, vehicleId, entryId = null)
                harness.collect(holder.state)

                holder.setLitersScaled(45_123L)
                holder.setPricePerLiterScaled(1_789L)
                val derivedState = holder.state.first { state -> state.totalCostMinor == 8_073L }

                assertEquals(8_073L, derivedState.totalCostMinor)
                assertNull(derivedState.message)
            } finally {
                harness.close()
            }
        }

    @Test
    fun invalidLiveMoneyClearsDerivedValueAndWaitsUntilSaveToPublishError() =
        runTest {
            val harness = AppGraphTestHarness(buildFuelGraph(), backgroundScope)
            val graph = harness.graph

            try {
                val vehicleId = createVehicle(harness, initialOdometerKm = 100L)
                val holder = graph.fuelEntryFormStateHolder(harness.scope, vehicleId, entryId = null)
                harness.collect(holder.state)

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
                harness.close()
            }
        }

    @Test
    fun inconsistentPartialEntryRequiresConfirmationThenPublishesBothIndicators() =
        runTest {
            val harness = AppGraphTestHarness(buildFuelGraph(), backgroundScope)
            val graph = harness.graph

            try {
                val vehicleId = createVehicle(harness, initialOdometerKm = 100L)
                val list = graph.fuelEntryListStateHolder(harness.scope, vehicleId)
                val form = graph.fuelEntryFormStateHolder(harness.scope, vehicleId, entryId = null)
                var saveCompletionCount = 0
                harness.collect(list.state)
                harness.collect(form.state)
                harness.collect(form.observeSaveCompletions()) {
                    saveCompletionCount += 1
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
                harness.close()
            }
        }

    @Test
    fun listPublishesWeightedSummaryAndReliabilityAfterThreeFullTanks() =
        runTest {
            val harness = AppGraphTestHarness(buildFuelGraph(), backgroundScope)
            val graph = harness.graph

            try {
                val vehicleId = createVehicle(harness, initialOdometerKm = 100L)
                val list = graph.fuelEntryListStateHolder(harness.scope, vehicleId)
                harness.collect(list.state)

                saveFullEntry(harness, list, vehicleId, odometerKm = 100L, expectedCount = 1)
                saveFullEntry(harness, list, vehicleId, odometerKm = 500L, expectedCount = 2)
                saveFullEntry(harness, list, vehicleId, odometerKm = 900L, expectedCount = 3)

                val state =
                    list.state.first { value ->
                        value.entries.size == 3 &&
                            value.consumptionAverageScaled == 1_000L &&
                            value.validConsumptionSegmentCount == 2 &&
                            value.isConsumptionReliable
                    }
                assertEquals(3, state.entries.size)
                assertEquals(1_000L, state.consumptionAverageScaled)
                assertEquals(2, state.validConsumptionSegmentCount)
                assertTrue(state.isConsumptionReliable)
                assertEquals(SyncStatus.Idle, state.syncStatus)
            } finally {
                harness.close()
            }
        }

    @Test
    fun deleteRequiresConfirmationAndRemovesTheEntryFromTheReactiveList() =
        runTest {
            val harness = AppGraphTestHarness(buildFuelGraph(), backgroundScope)
            val graph = harness.graph

            try {
                val vehicleId = createVehicle(harness, initialOdometerKm = 100L)
                val list = graph.fuelEntryListStateHolder(harness.scope, vehicleId)
                harness.collect(list.state)
                saveFullEntry(harness, list, vehicleId, odometerKm = 100L, expectedCount = 1)
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
                harness.close()
            }
        }

    private suspend fun createVehicle(
        harness: AppGraphTestHarness,
        initialOdometerKm: Long,
    ): String {
        val holder = harness.graph.vehicleFormStateHolder(harness.scope, vehicleId = null)
        harness.collect(holder.state)
        holder.setName("Roadster")
        holder.setInitialOdometerKm(initialOdometerKm)
        holder.save()
        val state = holder.state.first { value -> value.savedVehicleId != null && !value.isSaving }
        holder.close()
        return requireNotNull(state.savedVehicleId)
    }

    private suspend fun saveFullEntry(
        harness: AppGraphTestHarness,
        list: FuelEntryListStateHolder,
        vehicleId: String,
        odometerKm: Long,
        expectedCount: Int,
    ) {
        val holder = harness.graph.fuelEntryFormStateHolder(harness.scope, vehicleId, entryId = null)
        harness.collect(holder.state)
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
