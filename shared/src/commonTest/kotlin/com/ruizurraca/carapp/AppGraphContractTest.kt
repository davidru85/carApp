package com.ruizurraca.carapp

import com.ruizurraca.carapp.feature.vehicle.presentation.VehicleFormStateHolder
import com.ruizurraca.carapp.feature.vehicle.presentation.VehicleListStateHolder
import com.ruizurraca.carapp.shared.testing.testAppGraphDependencies
import com.ruizurraca.carapp.shared.testing.testAppProviders
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

class AppGraphContractTest {
    @Test
    fun kotlinGraphReturnsRealVehicleHoldersWithCallerOwnedScopes() =
        runTest {
            val dependencies = testAppGraphDependencies()
            val graph: AppGraph =
                buildAppGraph(
                    isDebugBuild = true,
                    providers = testAppProviders(dependencies),
                )
            val harness = AppGraphTestHarness(graph, backgroundScope)

            try {
                assertIs<VehicleListStateHolder>(graph.vehicleListStateHolder(harness.scope))
                assertIs<VehicleFormStateHolder>(graph.vehicleFormStateHolder(harness.scope, null))
            } finally {
                harness.close()
            }
        }
}
