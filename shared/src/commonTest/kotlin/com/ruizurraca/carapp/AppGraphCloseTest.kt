package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.common.DispatcherProvider
import com.ruizurraca.carapp.core.testing.InMemoryDatabaseFactory
import com.ruizurraca.carapp.shared.testing.testAppGraphDependencies
import com.ruizurraca.carapp.shared.testing.testAppProviders
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class AppGraphCloseTest {
    @Test
    fun kotlinGraphCloseReleasesItsDatabaseConnection() =
        runTest {
            assertGraphCloseReleasesDatabase { graph, _ ->
                graph.close()
                graph.close()
            }
        }

    @Test
    fun swiftGraphCloseTransitivelyReleasesItsDatabaseConnection() =
        runTest {
            assertGraphCloseReleasesDatabase { graph, dispatchers ->
                val swiftGraph = wrapAppGraphForSwift(graph, dispatchers)
                swiftGraph.close()
                swiftGraph.close()
            }
        }

    @Test
    fun kotlinGraphCanCloseImmediatelyWhileSettingsBootstrapStartsWithoutAConsumer() =
        runTest {
            val owningFactory = InMemoryDatabaseFactory()
            val recordingFactory = RecordingDatabaseFactory(owningFactory)
            val graph =
                buildAppGraph(
                    isDebugBuild = true,
                    providers =
                        testAppProviders(
                            testAppGraphDependencies(databaseFactory = recordingFactory),
                        ),
                )

            try {
                graph.close()
                advanceUntilIdle()

                assertEquals(1, recordingFactory.closeCalls)
            } finally {
                owningFactory.close()
            }
        }

    private fun assertGraphCloseReleasesDatabase(closeGraph: (AppGraph, DispatcherProvider) -> Unit) {
        val owningFactory = InMemoryDatabaseFactory()
        val recordingFactory = RecordingDatabaseFactory(owningFactory)
        val dependencies =
            testAppGraphDependencies(
                databaseFactory = recordingFactory,
            )
        val graph =
            buildAppGraph(
                isDebugBuild = true,
                providers = testAppProviders(dependencies),
            )

        try {
            closeGraph(graph, dependencies.dispatchers)
            assertEquals(1, recordingFactory.closeCalls)
        } finally {
            owningFactory.close()
        }
    }
}
