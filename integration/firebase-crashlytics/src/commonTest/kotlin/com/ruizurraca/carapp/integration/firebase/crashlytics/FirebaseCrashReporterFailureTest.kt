package com.ruizurraca.carapp.integration.firebase.crashlytics

import com.ruizurraca.carapp.core.common.UnexpectedError
import kotlin.test.Test

/**
 * `E4-04`: a provider failure MUST NOT reach the caller of `CrashReporter`.
 *
 * `docs/CONTRACTS.md §20.3.1` declares both entry points as non-suspending `Unit` functions with no
 * error channel, and they are called from failure paths. A crash reporter that threw there would
 * turn a recorded failure into an unhandled one, which is the worst outcome for a feature that exists
 * only to observe.
 */
class FirebaseCrashReporterFailureTest {
    private val unexpected =
        UnexpectedError(origin = ":core:sync", throwableClassName = "IllegalStateException")

    @Test
    fun aFailingProviderDoesNotPropagateFromRecordNonFatal() {
        val reporter = FirebaseCrashReporter(enabled = true, gateway = ThrowingCrashlyticsGateway())

        reporter.recordNonFatal(unexpected, mapOf("code" to "UNEXPECTED"))
    }

    @Test
    fun aFailingProviderDoesNotPropagateFromSetEnabled() {
        val reporter = FirebaseCrashReporter(gateway = ThrowingCrashlyticsGateway())

        reporter.setEnabled(true)
        reporter.setEnabled(false)
    }

    private class ThrowingCrashlyticsGateway : CrashlyticsGateway {
        override fun setCustomKeys(keys: Map<String, String>): Unit = error("provider unavailable")

        override fun recordException(exception: Throwable): Unit = error("provider unavailable")

        override fun setCollectionEnabled(enabled: Boolean): Unit = error("provider unavailable")

        override fun deleteUnsentReports(): Unit = error("provider unavailable")
    }
}
