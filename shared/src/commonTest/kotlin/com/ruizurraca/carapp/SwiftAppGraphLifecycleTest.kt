package com.ruizurraca.carapp

import app.cash.sqldelight.async.coroutines.awaitAsList
import com.ruizurraca.carapp.core.database.DatabaseFactory
import com.ruizurraca.carapp.core.database.DatabaseHandle
import com.ruizurraca.carapp.core.testing.InMemoryDatabaseFactory
import com.ruizurraca.carapp.feature.vehicle.presentation.VehicleFormStateHolder
import com.ruizurraca.carapp.shared.testing.testAppGraphDependencies
import com.ruizurraca.carapp.shared.testing.testAppProviders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class SwiftAppGraphLifecycleTest {
    @Test
    fun identicalArgumentsReuseTheSameCachedHolderAndDifferentOnesDoNot() =
        runTest {
            val owningFactory = InMemoryDatabaseFactory()
            val databaseHandle = owningFactory.create()
            val dependencies =
                confinedGraphDependencies(
                    testAppGraphDependencies(
                        databaseFactory = fixedDatabaseFactory(databaseHandle),
                    ),
                )
            val swiftGraph =
                wrapAppGraphForSwift(
                    buildAppGraph(isDebugBuild = true, providers = testAppProviders(dependencies)),
                    dependencies.dispatchers,
                )

            try {
                assertSame(
                    swiftGraph.vehicleListStateHolder(),
                    swiftGraph.vehicleListStateHolder(),
                    "the vehicle list holder is cached for its single key",
                )
                assertSame(
                    swiftGraph.sessionStateHolder(),
                    swiftGraph.sessionStateHolder(),
                    "the session holder is cached",
                )
                assertSame(
                    swiftGraph.syncStateHolder(),
                    swiftGraph.syncStateHolder(),
                    "the sync holder is cached",
                )
                assertSame(
                    swiftGraph.fuelEntryListStateHolder("vehicle-1"),
                    swiftGraph.fuelEntryListStateHolder("vehicle-1"),
                    "a fuel entry list holder is cached per vehicle",
                )
                assertNotSame(
                    swiftGraph.fuelEntryListStateHolder("vehicle-1"),
                    swiftGraph.fuelEntryListStateHolder("vehicle-2"),
                    "a different vehicle gets its own holder",
                )
                assertSame(
                    swiftGraph.fuelEntryFormStateHolder("vehicle-1", "entry-1"),
                    swiftGraph.fuelEntryFormStateHolder("vehicle-1", "entry-1"),
                    "a fuel entry form holder is cached per (vehicle, entry)",
                )
                assertNotSame(
                    swiftGraph.fuelEntryFormStateHolder("vehicle-1", "entry-1"),
                    swiftGraph.fuelEntryFormStateHolder("vehicle-1", null),
                    "the create form is a different key from an edit form",
                )
            } finally {
                swiftGraph.close()
                owningFactory.close()
            }
        }

    @Test
    fun everyFactoryThrowsAfterCloseExceptCloseItself() =
        runTest {
            val owningFactory = InMemoryDatabaseFactory()
            val databaseHandle = owningFactory.create()
            val dependencies =
                confinedGraphDependencies(
                    testAppGraphDependencies(
                        databaseFactory = fixedDatabaseFactory(databaseHandle),
                    ),
                )
            val swiftGraph =
                wrapAppGraphForSwift(
                    buildAppGraph(isDebugBuild = true, providers = testAppProviders(dependencies)),
                    dependencies.dispatchers,
                )

            try {
                swiftGraph.close()

                val factories: List<Pair<String, () -> Unit>> =
                    listOf(
                        "vehicleListStateHolder" to { swiftGraph.vehicleListStateHolder() },
                        "vehicleFormStateHolder" to { swiftGraph.vehicleFormStateHolder(null) },
                        "fuelEntryListStateHolder" to { swiftGraph.fuelEntryListStateHolder("vehicle-1") },
                        "fuelEntryFormStateHolder" to { swiftGraph.fuelEntryFormStateHolder("vehicle-1", null) },
                        "sessionStateHolder" to { swiftGraph.sessionStateHolder() },
                        "syncStateHolder" to { swiftGraph.syncStateHolder() },
                        "settingsStateHolder" to { swiftGraph.settingsStateHolder() },
                    )

                factories.forEach { (name, factory) ->
                    assertFailsWith<IllegalStateException>(name) { factory() }
                }

                // A second close is idempotent rather than a failure: it is a release, not a read.
                swiftGraph.close()
            } finally {
                owningFactory.close()
            }
        }

    @Test
    fun theSettingsHolderIsCachedByTheSwiftGraph() =
        runTest {
            val owningFactory = InMemoryDatabaseFactory()
            val databaseHandle = owningFactory.create()
            val dependencies =
                confinedGraphDependencies(
                    testAppGraphDependencies(
                        databaseFactory = fixedDatabaseFactory(databaseHandle),
                    ),
                )
            val swiftGraph =
                wrapAppGraphForSwift(
                    buildAppGraph(isDebugBuild = true, providers = testAppProviders(dependencies)),
                    dependencies.dispatchers,
                )

            try {
                assertSame(
                    swiftGraph.settingsStateHolder(),
                    swiftGraph.settingsStateHolder(),
                    "the settings holder is cached for the graph, like the session and sync holders",
                )
            } finally {
                swiftGraph.close()
                owningFactory.close()
            }
        }

    @Test
    fun closingTheSwiftGraphCancelsTheSettingsHolderScope() =
        runTest {
            val owningFactory = InMemoryDatabaseFactory()
            val databaseHandle = owningFactory.create()
            val dependencies =
                confinedGraphDependencies(
                    testAppGraphDependencies(
                        databaseFactory = fixedDatabaseFactory(databaseHandle),
                    ),
                )
            val recordingGraph =
                RecordingSettingsScopesAppGraph(
                    buildAppGraph(
                        isDebugBuild = true,
                        providers = testAppProviders(dependencies),
                    ),
                )
            val swiftGraph = wrapAppGraphForSwift(recordingGraph, dependencies.dispatchers)

            try {
                swiftGraph.settingsStateHolder()
                swiftGraph.close()

                assertFalse(
                    checkNotNull(recordingGraph.settingsScopes.single().coroutineContext[Job]).isActive,
                    "SwiftAppGraph.close() cancels the child scope it created for the settings holder",
                )
            } finally {
                swiftGraph.close()
                owningFactory.close()
            }
        }

    @Test
    fun releaseCancelsTheCachedCreationFormAndTheNextFlowCreatesANewVehicle() =
        runTest {
            val owningFactory = InMemoryDatabaseFactory()
            val databaseHandle = owningFactory.create()
            val dependencies =
                confinedGraphDependencies(
                    testAppGraphDependencies(
                        databaseFactory = fixedDatabaseFactory(databaseHandle),
                    ),
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
                first.state.awaitState("first Swift vehicle saved") { state ->
                    state.savedVehicleId != null && !state.isSaving
                }

                swiftGraph.releaseVehicleFormStateHolder(vehicleId = null)

                assertFalse(
                    recordingGraph.vehicleFormScopes
                        .single()
                        .coroutineContext[Job]!!
                        .isActive,
                )
                val second = swiftGraph.vehicleFormStateHolder(vehicleId = null)
                assertNotSame(first, second)
                assertEquals("", second.state.value.name)
                assertEquals(0, second.state.value.initialOdometerKm)
                second.setName("Second vehicle")
                second.save()
                second.state.awaitState("second Swift vehicle saved") { state ->
                    state.savedVehicleId != null && !state.isSaving
                }

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

private class RecordingSettingsScopesAppGraph(
    private val delegate: AppGraph,
) : AppGraph by delegate {
    val settingsScopes = mutableListOf<CoroutineScope>()

    override fun settingsStateHolder(scope: CoroutineScope): SettingsStateHolder {
        settingsScopes += scope
        return delegate.settingsStateHolder(scope)
    }
}

private fun fixedDatabaseFactory(databaseHandle: DatabaseHandle): DatabaseFactory =
    object : DatabaseFactory {
        override fun create() = databaseHandle
    }
