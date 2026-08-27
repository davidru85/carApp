package com.ruizurraca.carapp

import com.ruizurraca.carapp.shared.testing.testAppGraphDependencies
import com.ruizurraca.carapp.shared.testing.testAppProviders
import kotlin.test.Test
import kotlin.test.assertEquals

class BuildAppGraphTest {
    @Test
    fun explicitProvidersReachGraphAndFactoryOwnsBuildMode() {
        val suppliedDependencies = testAppGraphDependencies(isDebugBuild = true)

        val graph =
            buildAppGraph(
                isDebugBuild = false,
                providers = testAppProviders(suppliedDependencies),
            )

        assertEquals(
            suppliedDependencies.copy(isDebugBuild = false),
            graph.dependencies,
        )
    }
}
