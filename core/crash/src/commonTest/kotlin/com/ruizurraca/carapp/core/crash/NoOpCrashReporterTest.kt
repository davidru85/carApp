package com.ruizurraca.carapp.core.crash

import com.ruizurraca.carapp.core.common.UnexpectedError
import kotlin.test.Test

class NoOpCrashReporterTest {
    @Test
    fun recordingAndTogglingAreSilentAndNeverThrow() {
        val reporter: CrashReporter = NoOpCrashReporter

        reporter.setEnabled(true)
        reporter.recordNonFatal(
            UnexpectedError(origin = ":core:crash", throwableClassName = "IllegalStateException"),
            mapOf("code" to "UNEXPECTED"),
        )
        reporter.setEnabled(false)
        reporter.recordNonFatal(
            UnexpectedError(origin = ":core:crash", throwableClassName = "IllegalStateException"),
            emptyMap(),
        )
    }
}
