package com.ruizurraca.carapp.core.testing

import com.ruizurraca.carapp.core.analytics.AnalyticsEvent
import com.ruizurraca.carapp.core.analytics.AnalyticsUserProperties
import com.ruizurraca.carapp.core.analytics.CountBucket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `docs/CONTRACTS.md §16.1`: collection is disabled by default, and while disabled `track` and
 * `setUserProperties` are no-ops that buffer nothing.
 */
class AnalyticsOptInTest {
    private val properties = AnalyticsUserProperties(CountBucket.ONE, CountBucket.TWO_TO_FIVE)

    @Test
    fun collectionIsDisabledByDefault() {
        val tracker = RecordingAnalyticsTracker()

        assertEquals(false, tracker.isEnabled)

        tracker.track(AnalyticsEvent.OnboardingStarted)
        tracker.setUserProperties(properties)

        assertTrue(tracker.events.isEmpty())
        assertTrue(tracker.userProperties.isEmpty())
    }

    @Test
    fun enablingDoesNotReplayAnythingRecordedWhileDisabled() {
        val tracker = RecordingAnalyticsTracker()
        tracker.track(AnalyticsEvent.OnboardingStarted)
        tracker.setUserProperties(properties)

        tracker.setEnabled(true)

        assertTrue(
            tracker.events.isEmpty(),
            "Nothing is buffered while disabled, so opting in must not flush a backlog",
        )
        assertTrue(tracker.userProperties.isEmpty())
    }

    @Test
    fun disablingAgainStopsCollectionImmediately() {
        val tracker = RecordingAnalyticsTracker(initiallyEnabled = true)
        tracker.track(AnalyticsEvent.VehicleCreated)

        tracker.setEnabled(false)
        tracker.track(AnalyticsEvent.OnboardingCompleted)

        assertEquals(listOf(AnalyticsEvent.VehicleCreated), tracker.events)
    }

    @Test
    fun theNoOpTrackerRecordsNothingInEitherState() {
        NoOpAnalyticsTracker.setEnabled(true)
        NoOpAnalyticsTracker.track(AnalyticsEvent.VehicleCreated)
        NoOpAnalyticsTracker.setUserProperties(properties)
        NoOpAnalyticsTracker.setEnabled(false)
    }
}
