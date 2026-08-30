@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.sync.SyncController
import com.ruizurraca.carapp.feature.vehicle.presentation.VehicleFormStateHolder
import com.ruizurraca.carapp.feature.vehicle.presentation.VehicleListStateHolder
import com.ruizurraca.carapp.feature.vehicle.presentation.createVehicleFormStateHolder
import com.ruizurraca.carapp.feature.vehicle.presentation.createVehicleListStateHolder
import kotlinx.coroutines.CoroutineScope
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
interface AppGraph {
    fun vehicleListStateHolder(scope: CoroutineScope): VehicleListStateHolder

    fun vehicleFormStateHolder(
        scope: CoroutineScope,
        vehicleId: String?,
    ): VehicleFormStateHolder

    fun fuelEntryListStateHolder(
        scope: CoroutineScope,
        vehicleId: String,
    ): FuelEntryListStateHolder

    fun fuelEntryFormStateHolder(
        scope: CoroutineScope,
        vehicleId: String,
        entryId: String?,
    ): FuelEntryFormStateHolder

    fun sessionStateHolder(scope: CoroutineScope): SessionStateHolder

    fun syncController(): SyncController

    fun close()
}

internal class DefaultAppGraph(
    internal val dependencies: AppGraphDependencies,
) : AppGraph {
    private val vehicleRuntime = VehicleSliceRuntime(dependencies, dependencies.databaseFactory.create())
    private var closed = false

    override fun vehicleListStateHolder(scope: CoroutineScope): VehicleListStateHolder {
        checkOpen()
        return createVehicleListStateHolder(
            scope = scope,
            repository = vehicleRuntime.repository,
            dispatchers = dependencies.dispatchers,
            refreshVehicles = vehicleRuntime::refresh,
        )
    }

    override fun vehicleFormStateHolder(
        scope: CoroutineScope,
        vehicleId: String?,
    ): VehicleFormStateHolder {
        checkOpen()
        return createVehicleFormStateHolder(
            scope = scope,
            vehicleId = vehicleId,
            repository = vehicleRuntime.repository,
            dispatchers = dependencies.dispatchers,
            createVehicle = vehicleRuntime::createVehicle,
            updateVehicle = vehicleRuntime::updateVehicle,
        )
    }

    override fun fuelEntryListStateHolder(
        scope: CoroutineScope,
        vehicleId: String,
    ): FuelEntryListStateHolder {
        checkOpen()
        return FuelEntryListStateHolder(vehicleId)
    }

    override fun fuelEntryFormStateHolder(
        scope: CoroutineScope,
        vehicleId: String,
        entryId: String?,
    ): FuelEntryFormStateHolder {
        checkOpen()
        return FuelEntryFormStateHolder(vehicleId, entryId)
    }

    override fun sessionStateHolder(scope: CoroutineScope): SessionStateHolder {
        checkOpen()
        return SessionStateHolder(scope = scope, authClient = dependencies.authClient)
    }

    override fun syncController(): SyncController {
        checkOpen()
        error("SyncController is staged until E3-03 (D-88)")
    }

    override fun close() {
        if (closed) return
        closed = true
    }

    private fun checkOpen() {
        check(!closed) { "AppGraph is closed" }
    }
}
