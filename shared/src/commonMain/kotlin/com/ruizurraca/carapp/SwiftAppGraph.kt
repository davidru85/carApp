package com.ruizurraca.carapp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/** Swift-facing application graph facade. State-holder accessors are added by E0-07 in place. */
class SwiftAppGraph {
    private var backingDependencies: AppGraphDependencies? = null
    private var graphScope: CoroutineScope? = null
    private var vehicleSliceRuntime: VehicleSliceRuntime? = null
    private var cachedVehicleListStateHolder: VehicleListStateHolder? = null
    private var cachedSessionStateHolder: SessionStateHolder? = null

    constructor()

    internal constructor(dependencies: AppGraphDependencies) {
        backingDependencies = dependencies
        graphScope = CoroutineScope(SupervisorJob() + dependencies.dispatchers.main)
        vehicleSliceRuntime = VehicleSliceRuntime(dependencies, dependencies.databaseFactory.create())
    }

    internal val dependencies: AppGraphDependencies
        get() = checkNotNull(backingDependencies) { "SwiftAppGraph was not built from AppProviders" }

    fun vehicleListStateHolder(): VehicleListStateHolder =
        cachedVehicleListStateHolder
            ?: VehicleListStateHolder(
                scope = graphScope,
                vehicles = vehicleSliceRuntime?.observeVehicles(),
                refreshVehicles = vehicleSliceRuntime?.let { runtime -> runtime::refresh },
            ).also { cachedVehicleListStateHolder = it }

    fun vehicleFormStateHolder(vehicleId: String?): VehicleFormStateHolder =
        VehicleFormStateHolder(
            vehicleId = vehicleId,
            scope = graphScope,
            saveVehicle = vehicleSliceRuntime?.let { runtime -> runtime::save },
        )

    fun fuelEntryListStateHolder(vehicleId: String): FuelEntryListStateHolder = FuelEntryListStateHolder(vehicleId)

    fun fuelEntryFormStateHolder(
        vehicleId: String,
        entryId: String?,
    ): FuelEntryFormStateHolder = FuelEntryFormStateHolder(vehicleId, entryId)

    fun sessionStateHolder(): SessionStateHolder {
        val scope = checkNotNull(graphScope) { "SwiftAppGraph is closed or was not built from AppProviders" }
        return cachedSessionStateHolder
            ?: SessionStateHolder(
                scope = scope,
                authClient = dependencies.authClient,
            ).also { cachedSessionStateHolder = it }
    }

    fun syncStateHolder(): SyncStateHolder = SyncStateHolder()

    fun close() {
        cachedVehicleListStateHolder?.close()
        cachedVehicleListStateHolder = null
        cachedSessionStateHolder?.close()
        cachedSessionStateHolder = null
        graphScope?.cancel()
        graphScope = null
        vehicleSliceRuntime = null
        backingDependencies = null
    }
}
