package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.database.OwnerActiveRowCounts
import com.ruizurraca.carapp.core.model.CurrencyCode
import com.ruizurraca.carapp.core.model.DistanceUnit
import com.ruizurraca.carapp.core.model.UserSettings
import com.ruizurraca.carapp.core.model.VolumeUnit
import com.ruizurraca.carapp.core.testing.RecordingAnalyticsTracker
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.fail

/**
 * Opts a graph-backed analytics fixture in through the graph's opt-in gate (`E3-09`).
 *
 * Two things make this necessary rather than optional. Setting the flag on the tracker directly does
 * not survive: the graph's settings bootstrap publishes the freshly created row with
 * `analyticsEnabled = false` and the graph's own opt-in gate answers it, so the tracker is disabled
 * again a moment later. And the bucket count is a database read whose continuation runs on the
 * driver's dispatcher, which a virtual test scheduler cannot drain.
 *
 * The fixture therefore substitutes both seams `DefaultAppGraph` exposes for this story — the count
 * source and the settings flow, both `null` in production — and drains the virtual scheduler.
 * Everything runs without a wall-clock wait, so this fixture cannot interfere with the graph tests
 * that legitimately use real waits. The opt-in semantics are exercised by `SharedAnalyticsCadenceTest`
 * over `AnalyticsEmissions` and by the integration's own `FirebaseAnalyticsOptInTest`; the real
 * settings repository is driven into the gate end to end by `SettingsGraphTest` (`E4-01`).
 */
internal fun userSettings(
    analyticsEnabled: Boolean,
    currency: String = "EUR",
): UserSettings =
    UserSettings(
        currency = CurrencyCode(currency),
        distanceUnit = DistanceUnit.KM,
        volumeUnit = VolumeUnit.LITER,
        analyticsEnabled = analyticsEnabled,
    )

/**
 * Installs the two test seams a graph-backed analytics fixture needs, **before** the graph's own
 * collectors start. They must be in place before the first scheduler drain: the opt-in collector
 * subscribes to whichever settings flow the seam returns, and a seam installed afterwards would
 * leave it holding the real repository flow.
 */
internal fun AppGraph.installAnalyticsTestSeams(counts: () -> Pair<Int, Int>) {
    val defaultGraph = this as DefaultAppGraph
    defaultGraph.activeRowCountsForTest = {
        val (vehicles, entries) = counts()
        OwnerActiveRowCounts(vehicleCount = vehicles.toLong(), entryCount = entries.toLong())
    }
    defaultGraph.settingsOverrideForTest = flowOf(Outcome.Ok(userSettings(analyticsEnabled = true)))
}

/** Fails the fixture unless the opt-in the seams describe has actually been applied. */
internal fun RecordingAnalyticsTracker.assertOptedIn() {
    if (!isEnabled) fail("the fixture must reach the opted-in state through the graph's opt-in gate")
    if (userProperties.isEmpty()) fail("the opt-in must have emitted its buckets")
    clear()
}
