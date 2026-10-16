package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.analytics.AnalyticsEvent
import com.ruizurraca.carapp.core.analytics.AnalyticsUserProperties
import com.ruizurraca.carapp.core.analytics.CountBucket
import com.ruizurraca.carapp.core.auth.AuthClient
import com.ruizurraca.carapp.core.auth.AuthState
import com.ruizurraca.carapp.core.common.Confirmation
import com.ruizurraca.carapp.core.common.LocaleInfo
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.model.CurrencyCode
import com.ruizurraca.carapp.core.model.DistanceUnit
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.FuelType
import com.ruizurraca.carapp.core.model.VolumeUnit
import com.ruizurraca.carapp.core.testing.FakeAuthClient
import com.ruizurraca.carapp.core.testing.FakeLocaleProvider
import com.ruizurraca.carapp.core.testing.InMemoryDatabaseFactory
import com.ruizurraca.carapp.core.testing.RecordingAnalyticsTracker
import com.ruizurraca.carapp.feature.vehicle.domain.CreateVehicleCommand
import com.ruizurraca.carapp.shared.testing.testAppGraphDependencies
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * `E4-01` (`D-211`): the Settings holder over the **real** graph and its SQLDelight settings
 * repository.
 *
 * These fixtures close the `E3-09` residual: no earlier graph test drove the real repository into
 * the `docs/CONTRACTS.md §16.1` opt-in gate, because every opted-in fixture substituted the settings
 * flow through `DefaultAppGraph.settingsOverrideForTest`. Nothing is substituted here. The
 * repository's notifications resume through the database driver, which the virtual scheduler cannot
 * drain, so every wait is a bounded real-time expectation: `awaitState` for the holder's state and
 * [awaitGraphEffect] for calls the graph makes on the recording tracker (`D-204`).
 */
class SettingsGraphTest {
    @Test
    fun aFreshInstallPublishesTheLocaleCurrencyAndMetricUnitsWithAnalyticsOff() =
        runTest(timeout = SETTINGS_GRAPH_TEST_TIMEOUT) {
            withSettingsGraph(localeCurrency = "USD") { _, settings, _ ->
                val loaded =
                    settings.state.awaitState("fresh-install settings") { state ->
                        state.currencyCode != null
                    }

                assertEquals("USD", loaded.currencyCode, "the default currency is the locale currency")
                assertEquals(DistanceUnit.KM, loaded.distanceUnit)
                assertEquals(VolumeUnit.LITER, loaded.volumeUnit)
                assertFalse(loaded.analyticsEnabled, "analytics is off by default (§16.1)")
            }
        }

    @Test
    fun aFreshInstallWithAnUnsupportedLocaleCurrencyPublishesEur() =
        runTest(timeout = SETTINGS_GRAPH_TEST_TIMEOUT) {
            withSettingsGraph(localeCurrency = "JPY") { _, settings, _ ->
                val loaded =
                    settings.state.awaitState("fresh-install settings") { state ->
                        state.currencyCode != null
                    }

                assertEquals("EUR", loaded.currencyCode, "an unsupported locale currency falls back to EUR")
            }
        }

    @Test
    fun enablingAnalyticsThroughTheHolderOptsTheRunningGraphIn() =
        runTest(timeout = SETTINGS_GRAPH_TEST_TIMEOUT) {
            val tracker = RecordingAnalyticsTracker(initiallyEnabled = false)
            withSettingsGraph(tracker = tracker) { _, settings, _ ->
                optInThroughTheHolder(settings, tracker)

                assertEquals(
                    listOf(false, true),
                    tracker.enableCommands,
                    "the gate applied the default opt-out, then the opt-in, without a restart",
                )
                assertEquals(
                    listOf(AnalyticsUserProperties(CountBucket.ZERO, CountBucket.ZERO)),
                    tracker.userProperties,
                    "the opt-in sets the user properties exactly once",
                )
            }
        }

    @Test
    fun disablingAnalyticsThroughTheHolderTurnsTheTrackerOff() =
        runTest(timeout = SETTINGS_GRAPH_TEST_TIMEOUT) {
            val tracker = RecordingAnalyticsTracker(initiallyEnabled = false)
            withSettingsGraph(tracker = tracker) { _, settings, _ ->
                optInThroughTheHolder(settings, tracker)
                tracker.clear()

                settings.setAnalyticsEnabled(false)

                awaitGraphEffect("the gate applies the opt-out") { tracker.enableCommands == listOf(false) }
                assertFalse(tracker.isEnabled, "the opt-out reaches the tracker without a restart")
            }
        }

    @Test
    fun aProductEventMadeWhileOptedOutIsNeitherTrackedNorReplayed() =
        runTest(timeout = SETTINGS_GRAPH_TEST_TIMEOUT) {
            val tracker = RecordingAnalyticsTracker(initiallyEnabled = false)
            withSettingsGraph(tracker = tracker) { graph, settings, _ ->
                optInThroughTheHolder(settings, tracker)
                settings.setAnalyticsEnabled(false)
                settings.state.awaitState("the holder publishes the opt-out") { state ->
                    !state.analyticsEnabled && !state.isSaving
                }
                awaitGraphEffect("the gate applies the opt-out") { !tracker.isEnabled }
                tracker.clear()

                assertIs<Outcome.Ok<EntityId>>(graph.vehicleRuntimeForTest().createVehicle(vehicleCommand()))
                assertEquals(
                    emptyList<AnalyticsEvent>(),
                    tracker.events,
                    "an opted-out product event is not tracked",
                )

                settings.setAnalyticsEnabled(true)
                awaitGraphEffect("the gate applies the second opt-in") {
                    tracker.isEnabled && tracker.userProperties.isNotEmpty()
                }
                assertTrue(
                    tracker.events.none { event -> event == AnalyticsEvent.VehicleCreated },
                    "an event made while opted out is never buffered and replayed by a later opt-in",
                )
            }
        }

    @Test
    fun aCompletedLocalDataDeletionRepublishesTheDefaultsWithAnalyticsOff() =
        runTest(timeout = SETTINGS_GRAPH_TEST_TIMEOUT) {
            val authClient =
                FakeAuthClient(
                    initialState = AuthState.SignedIn(anonymousSession()),
                    unitResult = Outcome.Ok(Unit),
                )
            withSettingsGraph(localeCurrency = "USD", authClient = authClient) { graph, settings, harness ->
                val session = graph.sessionStateHolder(harness.scope)
                settings.state.awaitState("settings loaded in USD") { state ->
                    state.currencyCode == "USD" && !state.isSaving
                }
                settings.selectCurrency("GBP")
                settings.state.awaitState("currency changed to GBP") { state ->
                    state.currencyCode == "GBP" && !state.isSaving
                }
                settings.setAnalyticsEnabled(true)
                settings.state.awaitState("analytics enabled") { state -> state.analyticsEnabled && !state.isSaving }

                session.requestDeleteAccount()
                session.state.awaitState("delete-local-data confirmation") { state ->
                    state.message?.confirmation == Confirmation.DeleteLocalData
                }
                session.confirmDeleteAccount(Confirmation.DeleteLocalData)
                session.state.awaitState("local data deleted") { state -> state.phase == SessionPhase.SIGNED_OUT }

                val defaults =
                    settings.state.awaitState("defaults republished") { state ->
                        state.currencyCode == "USD"
                    }
                assertFalse(defaults.analyticsEnabled, "the recreated settings row has analytics off (§3)")
            }
        }

    /**
     * Opts the running graph in through the holder, exactly as a host's switch does, and returns once
     * the gate has applied it. The default opt-out is awaited first, so the opt-in is a transition the
     * gate observes rather than a value it could read before its first collection.
     */
    private suspend fun optInThroughTheHolder(
        settings: SettingsStateHolder,
        tracker: RecordingAnalyticsTracker,
    ) {
        settings.state.awaitState("settings loaded with analytics off") { state ->
            state.currencyCode != null && !state.analyticsEnabled && !state.isSaving
        }
        awaitGraphEffect("the gate applies the default opt-out") { tracker.enableCommands == listOf(false) }

        settings.setAnalyticsEnabled(true)

        settings.state.awaitState("the holder publishes the opt-in") { state ->
            state.analyticsEnabled && !state.isSaving
        }
        awaitGraphEffect("the gate applies the opt-in") {
            tracker.enableCommands.lastOrNull() == true && tracker.userProperties.isNotEmpty()
        }
    }

    private suspend fun TestScope.withSettingsGraph(
        localeCurrency: String = "USD",
        tracker: RecordingAnalyticsTracker = RecordingAnalyticsTracker(initiallyEnabled = false),
        authClient: AuthClient = FakeAuthClient(),
        block: suspend (AppGraph, SettingsStateHolder, AppGraphTestHarness) -> Unit,
    ) {
        val graph =
            DefaultAppGraph(
                confinedGraphDependencies(
                    testAppGraphDependencies(
                        databaseFactory = InMemoryDatabaseFactory(),
                        authClient = authClient,
                        analyticsTracker = tracker,
                        localeProvider =
                            FakeLocaleProvider(
                                LocaleInfo(
                                    languageTag = "en-US",
                                    region = "US",
                                    suggestedCurrency = CurrencyCode(localeCurrency),
                                ),
                            ),
                    ),
                ),
            )
        val harness = AppGraphTestHarness(graph, backgroundScope)
        try {
            val settings = graph.settingsStateHolder(harness.scope)
            // One subscriber for the whole test keeps the `WhileSubscribed` observation alive (§14).
            harness.collect(settings.state)
            block(graph, settings, harness)
        } finally {
            harness.close()
        }
    }

    private fun vehicleCommand(): CreateVehicleCommand =
        CreateVehicleCommand(
            name = "Roadster",
            initialOdometerKm = 1_000,
            brand = null,
            model = null,
            fuelType = FuelType.GASOLINE,
            confirmations = emptySet(),
        )
}

private val SETTINGS_GRAPH_TEST_TIMEOUT = GRAPH_STATE_EXPECTATION_TIMEOUT + 15.seconds
