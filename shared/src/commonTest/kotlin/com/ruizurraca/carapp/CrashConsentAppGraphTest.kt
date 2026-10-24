package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.testing.InMemoryDatabaseFactory
import com.ruizurraca.carapp.core.testing.RecordingCrashReporter
import com.ruizurraca.carapp.shared.testing.testAppGraphDependencies
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `E4-04` (`D-241`): the graph hands its own `CrashReporter` to the opt-in gate. A gate that coupled
 * the commands correctly but received the no-op default would pass `SharedCrashConsentTest` and still
 * leave crash collection undriven in the app, which is the failure this test catches.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CrashConsentAppGraphTest {
    @Test
    fun theGraphCommandsItsCrashReporterFromTheAnalyticsOptIn() =
        runTest {
            val crashReporter = RecordingCrashReporter()
            val graph =
                DefaultAppGraph(
                    confinedGraphDependencies(
                        testAppGraphDependencies(
                            databaseFactory = InMemoryDatabaseFactory(),
                            crashReporter = crashReporter,
                        ),
                    ),
                )
            // Installed before the first drain, as in SharedAnalyticsEmissionTest: the opt-in collector
            // subscribes to the override, which publishes analyticsEnabled = true.
            graph.installAnalyticsTestSeams { 0 to 0 }
            try {
                advanceUntilIdle()

                assertEquals(listOf(true), crashReporter.enableCommands)
            } finally {
                graph.close()
            }
        }
}
