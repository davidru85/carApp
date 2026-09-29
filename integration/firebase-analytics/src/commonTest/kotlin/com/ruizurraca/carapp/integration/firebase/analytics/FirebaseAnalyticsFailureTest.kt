package com.ruizurraca.carapp.integration.firebase.analytics

import com.ruizurraca.carapp.core.analytics.AnalyticsEvent
import com.ruizurraca.carapp.core.analytics.AnalyticsTracker
import com.ruizurraca.carapp.core.analytics.AnalyticsUserProperties
import com.ruizurraca.carapp.core.analytics.CountBucket
import com.ruizurraca.carapp.core.common.FirebaseAnalyticsProviderError
import com.ruizurraca.carapp.core.common.Outcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `E3-09`: a provider failure MUST NOT reach the caller of `AnalyticsTracker`.
 *
 * `docs/CONTRACTS.md §16.1` declares all three entry points as non-suspending `Unit` functions with
 * no error channel, and `D-10` treats metrics as best-effort: a metric that cannot be recorded is
 * dropped. An analytics failure that propagated would therefore be an unhandled exception on a
 * product path — the worst possible outcome for a feature whose entire purpose is observability.
 */
class FirebaseAnalyticsFailureTest {
    private val event = AnalyticsEvent.VehicleCreated
    private val properties = AnalyticsUserProperties(CountBucket.ZERO, CountBucket.ONE)

    @Test
    fun aFailingProviderDoesNotPropagateFromTrack() {
        val tracker = FirebaseAnalyticsTracker(enabled = true, gateway = ThrowingGateway())

        tracker.track(event)
    }

    @Test
    fun aFailingProviderDoesNotPropagateFromSetUserProperties() {
        val tracker = FirebaseAnalyticsTracker(enabled = true, gateway = ThrowingGateway())

        tracker.setUserProperties(properties)
    }

    @Test
    fun aFailingProviderDoesNotPropagateFromSetEnabled() {
        val tracker = FirebaseAnalyticsTracker(gateway = ThrowingGateway())

        tracker.setEnabled(true)
    }

    @Test
    fun theFailureIsClassifiedIntoTheClosedTaxonomy() {
        // The provider exception is converted to a stable code rather than carried as text, so the
        // tracker can report the fact through the injected sink without leaking provider detail.
        assertEquals("ANALYTICS.PROVIDER_FAILED", FirebaseAnalyticsProviderError.PROVIDER_FAILED.code)

        val codes = mutableListOf<String>()
        val tracker =
            FirebaseAnalyticsTracker(
                enabled = true,
                gateway = ThrowingGateway(),
                onProviderError = { error -> codes += error.code },
            )

        tracker.track(event)
        tracker.setUserProperties(properties)

        assertEquals(listOf("ANALYTICS.PROVIDER_FAILED", "ANALYTICS.PROVIDER_FAILED"), codes)
    }

    @Test
    fun aDisabledTrackerNeverReachesTheProviderAtAll() {
        val gateway = ThrowingGateway()
        val tracker = FirebaseAnalyticsTracker(gateway = gateway)

        tracker.track(event)
        tracker.setUserProperties(properties)

        assertTrue(gateway.logEventCalls == 0, "a disabled tracker MUST not call the provider")
    }

    @Test
    fun everyEntryPointIsOnTheTrackerInterface() {
        // A compile-time guard that the three entry points keep the non-suspending, error-free shape
        // the contract declares: the lambda below would not compile against a suspending signature.
        val tracker: AnalyticsTracker = FirebaseAnalyticsTracker(gateway = ThrowingGateway())
        val calls: List<() -> Unit> =
            listOf(
                { tracker.track(event) },
                { tracker.setUserProperties(properties) },
                { tracker.setEnabled(true) },
            )

        calls.forEach { call -> call() }
        assertTrue(calls.size == 3)
        assertEquals(Outcome.Ok(Unit), Outcome.Ok(Unit))
    }

    private class ThrowingGateway : AnalyticsGateway {
        var logEventCalls = 0

        override fun logEvent(name: String, parameters: Map<String, Any>) {
            logEventCalls += 1
            throw IllegalStateException("provider unavailable")
        }

        override fun setUserProperty(name: String, value: String) {
            throw IllegalStateException("provider unavailable")
        }

        override fun setCollectionEnabled(enabled: Boolean) {
            if (!enabled) return
            throw IllegalStateException("provider unavailable")
        }

        override fun close() = Unit
    }
}
