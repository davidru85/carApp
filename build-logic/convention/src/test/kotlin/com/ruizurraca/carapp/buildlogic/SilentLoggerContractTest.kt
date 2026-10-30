package com.ruizurraca.carapp.buildlogic

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `E4-04` (`D-242`): until `E4-08` delivers a redacting logger, the logger that `:wiring:firebase` binds
 * emits nothing. `FirebaseAppProvidersTest.theProvidersLoggerReadsNothingItIsGiven` observes reads of
 * `fields` and `throwable`, but a `String` argument cannot count its reads, so a logger that printed
 * `tag` or `message` would pass it. This guard pins the body itself.
 */
class SilentLoggerContractTest {
    private val repositoryRoot = File(checkNotNull(System.getProperty("carapp.repoRoot")))

    @Test
    fun theProvidersLoggerBodyIsEmpty() {
        val providers =
            repositoryRoot
                .resolve(
                    "wiring/firebase/src/commonMain/kotlin/com/ruizurraca/carapp/wiring/firebase/FirebaseAppProviders.kt",
                ).readText()
        val logger =
            providers
                .substringAfter("private fun stagedLogger(): Logger =", missingDelimiterValue = "")
                .substringBefore("private fun stagedLocaleProvider()")

        assertTrue(
            SILENT_LOG_OVERRIDE.containsMatchIn(logger),
            "stagedLogger() must override log(...) with the body `= Unit` until E4-08 (D-242)",
        )
        assertEquals(1, logger.count { character -> character == '{' }, "the logger object declares only log")
    }

    private companion object {
        val SILENT_LOG_OVERRIDE =
            Regex(
                """override fun log\(\s*level: LogLevel,\s*tag: String,\s*message: String,\s*""" +
                    """fields: Map<String, String>,\s*throwable: Throwable\?,\s*\) = Unit\s*}\s*$""",
            )
    }
}
