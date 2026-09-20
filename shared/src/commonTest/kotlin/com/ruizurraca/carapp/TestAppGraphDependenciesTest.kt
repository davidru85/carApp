package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.common.SyncStatus
import com.ruizurraca.carapp.shared.testing.testAppGraphDependencies
import com.ruizurraca.carapp.shared.testing.testAppProviders
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestAppGraphDependenciesTest {
    @Test
    fun defaultFactoryBuildsWithoutKoin() {
        val dependencies = testAppGraphDependencies()

        assertTrue(dependencies.isDebugBuild)
    }

    /**
     * The graph is constructible with no container: `buildAppGraph` takes the provider port, and
     * `testAppProviders` satisfies it from the canonical test dependencies. If anything in graph
     * construction reached for a Koin container, this would throw here rather than in a test that
     * merely inspects a value object.
     */
    @Test
    fun graphConstructsAndServesStateHoldersWithoutAContainer() =
        runTest {
            val dependencies = confinedGraphDependencies(testAppGraphDependencies())
            val graph = buildAppGraph(isDebugBuild = true, providers = testAppProviders(dependencies))

            try {
                val list = graph.vehicleListStateHolder(backgroundScope)
                assertEquals(SyncStatus.Idle, list.state.value.syncStatus)
                assertEquals(true, list.state.value.isLoading)
                assertEquals(0, list.state.value.vehicles.size)

                val session = graph.sessionStateHolder(backgroundScope)
                // The default fake reports `SignedOut`, and the holder publishes that resolved state
                // rather than the cold-start `UNKNOWN` the real provider begins with.
                assertEquals(SessionPhase.SIGNED_OUT, session.state.value.phase)

                val sync = graph.syncStateHolder(backgroundScope)
                assertEquals(SyncStatus.Idle, sync.state.value.status)
            } finally {
                graph.close()
            }
        }
}
