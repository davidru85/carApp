package com.ruizurraca.carapp.integration.firebase.analytics

import com.ruizurraca.carapp.core.analytics.AnalyticsEvent
import com.ruizurraca.carapp.core.analytics.AnalyticsUserProperties
import com.ruizurraca.carapp.core.analytics.CountBucket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `E3-09` acceptance criterion 2: collection is disabled at startup and enabled only after an
 * explicit opt-in, including on a fresh install, and **nothing is buffered while disabled**
 * (`docs/CONTRACTS.md §16.1`).
 *
 * The fresh-install case is the one an implementation is most likely to get wrong: the SDK's own
 * default is opt-in-until-told-otherwise, so the tracker MUST command the provider off at
 * construction rather than assume it starts off.
 */
class FirebaseAnalyticsOptInTest {
    private val properties =
        AnalyticsUserProperties(CountBucket.ONE, CountBucket.TWO_TO_FIVE)

    @Test
    fun aFreshInstallStartsWithCollectionDisabledAtTheProvider() {
        val gateway = RecordingGateway()

        FirebaseAnalyticsTracker(gateway = gateway)

        assertEquals(
            listOf(false),
            gateway.collectionStates,
            "the provider MUST be commanded off at construction, whatever its own default is",
        )
    }

    @Test
    fun nothingIsBufferedWhileDisabledAndNothingIsReplayedOnEnable() {
        val gateway = RecordingGateway()
        val tracker = FirebaseAnalyticsTracker(gateway = gateway)

        tracker.track(AnalyticsEvent.OnboardingStarted)
        tracker.setUserProperties(properties)

        assertEquals(emptyList(), gateway.loggedEvents, "events are dropped, not queued")
        assertEquals(emptyList(), gateway.userProperties, "properties are dropped, not queued")

        tracker.setEnabled(true)

        assertEquals(
            emptyList(),
            gateway.loggedEvents,
            "enabling MUST NOT replay what happened while disabled",
        )
        assertEquals(
            emptyList(),
            gateway.userProperties,
            "enabling MUST NOT replay what happened while disabled",
        )
    }

    @Test
    fun disablingStopsCollectionImmediately() {
        val gateway = RecordingGateway()
        val tracker = FirebaseAnalyticsTracker(enabled = true, gateway = gateway)

        tracker.track(AnalyticsEvent.VehicleCreated)
        tracker.setEnabled(false)
        tracker.track(AnalyticsEvent.OnboardingCompleted)
        tracker.setUserProperties(properties)

        assertEquals(listOf("vehicle_created"), gateway.loggedEvents.map { it.name })
        assertEquals(emptyList(), gateway.userProperties)
        assertEquals(
            listOf(true, false),
            gateway.collectionStates,
            "an instance that starts enabled commands the provider on at construction, then off",
        )
    }

    @Test
    fun theOptInFlagIsForwardedToTheProviderOnEveryChange() {
        val gateway = RecordingGateway()
        val tracker = FirebaseAnalyticsTracker(gateway = gateway)

        tracker.setEnabled(true)
        tracker.setEnabled(false)
        tracker.setEnabled(true)

        assertEquals(listOf(false, true, false, true), gateway.collectionStates)
    }

    @Test
    fun userPropertiesAreBucketedAndNeverExactFigures() {
        val gateway = RecordingGateway()
        val tracker = FirebaseAnalyticsTracker(enabled = true, gateway = gateway)

        tracker.setUserProperties(properties)

        val recorded = gateway.userProperties.fold(emptyMap<String, String>()) { acc, entry -> acc + entry }
        assertEquals(
            mapOf(
                "vehicle_count_bucket" to "ONE",
                "entry_count_bucket" to "TWO_TO_FIVE",
            ),
            recorded,
        )
        assertTrue(
            recorded.values.all { value -> value.toIntOrNull() == null },
            "a bucket name MUST NOT be sent as a number",
        )
    }

    @Test
    fun aDisabledTrackerIsASilentNoOpForEveryEntryPoint() {
        val gateway = RecordingGateway()
        val tracker = FirebaseAnalyticsTracker(gateway = gateway)

        tracker.track(AnalyticsEvent.AccountConversionStarted)
        tracker.setUserProperties(properties)

        assertEquals(emptyList(), gateway.loggedEvents)
        assertEquals(emptyList(), gateway.userProperties)
    }
}
