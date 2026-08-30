package com.ruizurraca.carapp

import app.cash.sqldelight.async.coroutines.awaitAsList
import com.ruizurraca.carapp.core.database.DatabaseHandle
import com.ruizurraca.carapp.core.database.DatabaseFactory
import com.ruizurraca.carapp.core.testing.InMemoryDatabaseFactory
import com.ruizurraca.carapp.feature.vehicle.presentation.VehicleFormStateHolder
import com.ruizurraca.carapp.shared.testing.testAppGraphDependencies
import com.ruizurraca.carapp.shared.testing.testAppProviders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class SwiftAppGraphLifecycleTest {
    @Test
    fun releaseCancelsTheCachedCreationFormAndTheNextFlowCreatesANewVehicle() =
        runTest {
            val owningFactory = InMemoryDatabaseFactory()
            val databaseHandle = owningFactory.create()
            val dependencies =
                testAppGraphDependencies(
                    databaseFactory = fixedDatabaseFactory(databaseHandle),
                )
            val recordingGraph =
                RecordingVehicleFormScopesAppGraph(
                    buildAppGraph(
                        isDebugBuild = true,
                        providers = testAppProviders(dependencies),
                    ),
                )
            val swiftGraph = wrapAppGraphForSwift(recordingGraph, dependencies.dispatchers)

            try {
                val first = swiftGraph.vehicleFormStateHolder(vehicleId = null)
                assertSame(first, swiftGraph.vehicleFormStateHolder(vehicleId = null))
                first.setName("First vehicle")
                first.save()

                swiftGraph.releaseVehicleFormStateHolder(vehicleId = null)

                assertFalse(recordingGraph.vehicleFormScopes.single().coroutineContext[Job]!!.isActive)
                val second = swiftGraph.vehicleFormStateHolder(vehicleId = null)
                assertNotSame(first, second)
                assertEquals("", second.state.value.name)
                assertEquals(0, second.state.value.initialOdometerKm)
                second.setName("Second vehicle")
                second.save()

                assertEquals(
                    listOf("First vehicle", "Second vehicle"),
                    databaseHandle.database.databaseQueries
                        .selectAllVehicles()
                        .awaitAsList()
                        .map { vehicle -> vehicle.name },
                )
            } finally {
                swiftGraph.close()
                owningFactory.close()
            }
        }
}

private class RecordingVehicleFormScopesAppGraph(
    private val delegate: AppGraph,
) : AppGraph by delegate {
    val vehicleFormScopes = mutableListOf<CoroutineScope>()

    override fun vehicleFormStateHolder(
        scope: CoroutineScope,
        vehicleId: String?,
    ): VehicleFormStateHolder {
        vehicleFormScopes += scope
        return delegate.vehicleFormStateHolder(scope, vehicleId)
    }
}

private fun fixedDatabaseFactory(databaseHandle: DatabaseHandle): DatabaseFactory =
    object : DatabaseFactory {
        override fun create() = databaseHandle
    }
