package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.crash.CrashReporter
import com.ruizurraca.carapp.core.testing.InMemoryDatabaseFactory
import com.ruizurraca.carapp.core.testing.RecordingCrashReporter
import com.ruizurraca.carapp.shared.testing.testAppGraphDependencies
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertSame

/**
 * `E4-04` (`D-243`): the graph hands every chokepoint the policy bound to its own `CrashReporter`.
 * A chokepoint that applied the policy correctly but received the no-op would pass the unit tests and
 * still record nothing in the app, which is the failure this test catches.
 */
class NonFatalReportingAppGraphTest {
    @Test
    fun theVehicleHoldersReceiveARepositoryReportingToTheGraphCrashReporter() =
        runTest {
            withGraph { graph, crashReporter ->
                val repository = assertIs<NonFatalReportingVehicleRepository>(graph.vehicleRuntimeForTest().repository)
                assertSame<CrashReporter>(crashReporter, repository.nonFatals.crashReporter)
            }
        }

    @Test
    fun theFuelHoldersReceiveARepositoryReportingToTheGraphCrashReporter() =
        runTest {
            withGraph { graph, crashReporter ->
                val repository = assertIs<NonFatalReportingFuelEntryRepository>(graph.fuelEntryRepositoryForTest())
                assertSame<CrashReporter>(crashReporter, repository.nonFatals.crashReporter)
            }
        }

    @Test
    fun theSessionHolderReportsToTheGraphCrashReporter() =
        runTest {
            withGraph { graph, crashReporter ->
                val holder = graph.sessionStateHolder(backgroundScope)
                try {
                    assertSame<CrashReporter>(crashReporter, holder.nonFatalsForTest.crashReporter)
                } finally {
                    holder.close()
                }
            }
        }

    @Test
    fun theSettingsHolderReceivesARepositoryReportingToTheGraphCrashReporter() =
        runTest {
            withGraph { graph, crashReporter ->
                val repository =
                    assertIs<NonFatalReportingSettingsRepository>(
                        (graph as DefaultAppGraph).settingsHolderRepositoryForTest,
                    )
                assertSame<CrashReporter>(crashReporter, repository.nonFatals.crashReporter)
            }
        }

    private suspend fun TestScope.withGraph(block: suspend (AppGraph, RecordingCrashReporter) -> Unit) {
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
        try {
            block(graph, crashReporter)
        } finally {
            graph.close()
        }
    }
}
