package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.common.DispatcherProvider
import com.ruizurraca.carapp.core.database.DatabaseHandle
import com.ruizurraca.carapp.core.database.DatabaseFactory
import com.ruizurraca.carapp.core.testing.InMemoryDatabaseFactory
import com.ruizurraca.carapp.shared.testing.testAppGraphDependencies
import com.ruizurraca.carapp.shared.testing.testAppProviders
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

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

    private suspend fun assertGraphCloseReleasesDatabase(closeGraph: (AppGraph, DispatcherProvider) -> Unit) {
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

private class RecordingDatabaseFactory(
    private val delegate: DatabaseFactory,
) : DatabaseFactory {
    var closeCalls: Int = 0
        private set

    override fun create(): DatabaseHandle {
        val handle = delegate.create()
        return object : DatabaseHandle {
            override val database = handle.database

            override fun close() {
                closeCalls += 1
                handle.close()
            }
        }
    }
}
