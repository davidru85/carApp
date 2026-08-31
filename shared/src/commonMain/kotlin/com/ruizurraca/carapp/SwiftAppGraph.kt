package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.common.DispatcherProvider
import com.ruizurraca.carapp.feature.fuel.presentation.FuelEntryFormStateHolder
import com.ruizurraca.carapp.feature.fuel.presentation.FuelEntryListStateHolder
import com.ruizurraca.carapp.feature.vehicle.presentation.VehicleFormStateHolder
import com.ruizurraca.carapp.feature.vehicle.presentation.VehicleListStateHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

/** Swift-facing application graph facade with graph-owned state-holder scopes (`D-86`). */
class SwiftAppGraph {
    private var backingGraph: AppGraph? = null
    private var dispatchers: DispatcherProvider? = null
    private var cachedVehicleList: ScopedHolder<VehicleListStateHolder>? = null
    private val cachedVehicleForms = mutableMapOf<String?, ScopedHolder<VehicleFormStateHolder>>()
    private val cachedFuelEntryLists = mutableMapOf<String, ScopedHolder<FuelEntryListStateHolder>>()
    private val cachedFuelEntryForms = mutableMapOf<Pair<String, String?>, ScopedHolder<FuelEntryFormStateHolder>>()
    private var cachedSession: ScopedHolder<SessionStateHolder>? = null
    private var cachedSync: ScopedHolder<SyncStateHolder>? = null
    private var closed = false

    constructor()

    internal constructor(
        graph: AppGraph,
        dispatchers: DispatcherProvider,
    ) {
        backingGraph = graph
        this.dispatchers = dispatchers
    }

    fun vehicleListStateHolder(): VehicleListStateHolder {
        val graph = requireOpenGraph()
        return cachedVehicleList?.holder
            ?: newScopedHolder { scope -> graph.vehicleListStateHolder(scope) }
                .also { cachedVehicleList = it }
                .holder
    }

    fun vehicleFormStateHolder(vehicleId: String?): VehicleFormStateHolder {
        val graph = requireOpenGraph()
        return cachedVehicleForms
            .getOrPut(vehicleId) {
                newScopedHolder { scope -> graph.vehicleFormStateHolder(scope, vehicleId) }
            }.holder
    }

    fun releaseVehicleFormStateHolder(vehicleId: String?) {
        cachedVehicleForms.release(vehicleId, VehicleFormStateHolder::close)
    }

    fun fuelEntryListStateHolder(vehicleId: String): FuelEntryListStateHolder {
        val graph = requireOpenGraph()
        return cachedFuelEntryLists
            .getOrPut(vehicleId) {
                newScopedHolder { scope -> graph.fuelEntryListStateHolder(scope, vehicleId) }
            }.holder
    }

    fun releaseFuelEntryListStateHolder(vehicleId: String) {
        cachedFuelEntryLists.release(vehicleId, FuelEntryListStateHolder::close)
    }

    fun fuelEntryFormStateHolder(
        vehicleId: String,
        entryId: String?,
    ): FuelEntryFormStateHolder {
        val graph = requireOpenGraph()
        return cachedFuelEntryForms
            .getOrPut(vehicleId to entryId) {
                newScopedHolder { scope -> graph.fuelEntryFormStateHolder(scope, vehicleId, entryId) }
            }.holder
    }

    fun releaseFuelEntryFormStateHolder(
        vehicleId: String,
        entryId: String?,
    ) {
        cachedFuelEntryForms.release(vehicleId to entryId, FuelEntryFormStateHolder::close)
    }

    fun sessionStateHolder(): SessionStateHolder {
        val graph = requireOpenGraph()
        return cachedSession?.holder
            ?: newScopedHolder { scope -> graph.sessionStateHolder(scope) }
                .also { cachedSession = it }
                .holder
    }

    fun syncStateHolder(): SyncStateHolder {
        requireOpenGraph()
        return cachedSync?.holder
            ?: newScopedHolder { SyncStateHolder() }
                .also { cachedSync = it }
                .holder
    }

    fun close() {
        if (closed) return
        closed = true
        cachedVehicleList?.close(VehicleListStateHolder::close)
        cachedVehicleForms.values.forEach { it.close(VehicleFormStateHolder::close) }
        cachedFuelEntryLists.values.forEach { it.close(FuelEntryListStateHolder::close) }
        cachedFuelEntryForms.values.forEach { it.close(FuelEntryFormStateHolder::close) }
        cachedSession?.close(SessionStateHolder::close)
        cachedSync?.close(SyncStateHolder::close)
        cachedVehicleList = null
        cachedVehicleForms.clear()
        cachedFuelEntryLists.clear()
        cachedFuelEntryForms.clear()
        cachedSession = null
        cachedSync = null
        backingGraph?.close()
        backingGraph = null
        dispatchers = null
    }

    private fun requireOpenGraph(): AppGraph {
        check(!closed) { "SwiftAppGraph is closed" }
        return checkNotNull(backingGraph) { "SwiftAppGraph was not created by createSwiftAppGraph" }
    }

    private fun <T> newScopedHolder(factory: (CoroutineScope) -> T): ScopedHolder<T> {
        val dispatcherProvider = checkNotNull(dispatchers)
        val scope = CoroutineScope(SupervisorJob() + dispatcherProvider.main)
        return ScopedHolder(scope = scope, holder = factory(scope))
    }
}

private data class ScopedHolder<T>(
    val scope: CoroutineScope,
    val holder: T,
) {
    fun close(closeHolder: T.() -> Unit) {
        holder.closeHolder()
        scope.cancel()
    }
}

private fun <K, T> MutableMap<K, ScopedHolder<T>>.release(
    key: K,
    closeHolder: T.() -> Unit,
) {
    remove(key)?.close(closeHolder)
}

@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
fun wrapAppGraphForSwift(
    graph: AppGraph,
    dispatchers: DispatcherProvider,
): SwiftAppGraph = SwiftAppGraph(graph = graph, dispatchers = dispatchers)
