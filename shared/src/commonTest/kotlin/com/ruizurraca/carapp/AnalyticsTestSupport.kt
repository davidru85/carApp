package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.testing.RecordingAnalyticsTracker
import com.ruizurraca.carapp.feature.session.domain.UpdateSettingsCommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * Opts a graph-backed analytics fixture in through the product path (`E3-09`).
 *
 * Setting the flag on the tracker directly does not survive: the graph's settings bootstrap publishes
 * the freshly created row with `analyticsEnabled = false`, and the graph's own opt-in gate is what
 * answers it, so the tracker is disabled again a moment later. Opting in through the repository is
 * therefore the only way to reach the state a real user reaches, and it exercises the same gate.
 */
internal suspend fun TestScope.optInAnalytics(
    graph: AppGraph,
    tracker: RecordingAnalyticsTracker,
    timeoutMillis: Long = OPT_IN_WAIT_MILLIS,
) {
    val repository = (graph as DefaultAppGraph).settingsRepositoryForTest
    repository.updateSettings(UpdateSettingsCommand(currency = null, analyticsEnabled = true))
    // The database notifies its observers on a real dispatcher, so the test must genuinely wait for
    // the persisted value before the confined collector can be drained.
    repository.settings.first { result ->
        result is com.ruizurraca.carapp.core.common.Outcome.Ok && result.value.analyticsEnabled
    }
    // And then wait for the opt-in's own emission, not only for the flag: the bucket count is a
    // database read whose continuation runs on a real dispatcher that virtual time cannot drain.
    // The wait is bounded, so a condition that never becomes true fails the fixture instead of
    // hanging it.
    withContext(Dispatchers.Default) {
        val start = TimeSource.Monotonic.markNow()
        while ((!tracker.isEnabled || tracker.userProperties.isEmpty()) &&
            start.elapsedNow() < timeoutMillis.milliseconds
        ) {
            delay(OPT_IN_WAIT_STEP_MILLIS)
        }
    }
    advanceUntilIdle()
    check(tracker.isEnabled) { "the fixture must reach the opted-in state through the product path" }
    check(tracker.userProperties.isNotEmpty()) { "the opt-in must have emitted its buckets" }
    tracker.clear()
}

private const val OPT_IN_WAIT_MILLIS = 10_000L
private const val OPT_IN_WAIT_STEP_MILLIS = 5L
