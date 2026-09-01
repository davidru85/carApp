@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.MinorUnits
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.SUPPORTED_CURRENCY_CODES
import com.ruizurraca.carapp.core.database.FuelEntryDatabaseAccess
import com.ruizurraca.carapp.core.model.CurrencyCode
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.Vehicle
import com.ruizurraca.carapp.core.sync.SyncController
import com.ruizurraca.carapp.feature.fuel.data.SqlDelightFuelEntryRepository
import com.ruizurraca.carapp.feature.fuel.presentation.FuelEntryFormStateHolder
import com.ruizurraca.carapp.feature.fuel.presentation.FuelEntryListStateHolder
import com.ruizurraca.carapp.feature.fuel.presentation.createFuelEntryFormStateHolder
import com.ruizurraca.carapp.feature.fuel.presentation.createFuelEntryListStateHolder
import com.ruizurraca.carapp.feature.vehicle.presentation.VehicleFormStateHolder
import com.ruizurraca.carapp.feature.vehicle.presentation.VehicleListStateHolder
import com.ruizurraca.carapp.feature.vehicle.presentation.createVehicleFormStateHolder
import com.ruizurraca.carapp.feature.vehicle.presentation.createVehicleListStateHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform
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
    private val databaseHandle = dependencies.databaseFactory.create()
    private val vehicleRuntime = VehicleSliceRuntime(dependencies, databaseHandle.database)
    private val fuelRepository =
        SqlDelightFuelEntryRepository(
            databaseAccess = FuelEntryDatabaseAccess(databaseHandle.database),
            ownerContext = dependencies.ownerContext,
            clock = dependencies.clock,
            uuidGenerator = dependencies.uuidGenerator,
        )
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
        return createFuelEntryListStateHolder(
            scope = scope,
            vehicleId = vehicleId,
            repository = fuelRepository,
            dispatchers = dependencies.dispatchers,
        )
    }

    override fun fuelEntryFormStateHolder(
        scope: CoroutineScope,
        vehicleId: String,
        entryId: String?,
    ): FuelEntryFormStateHolder {
        checkOpen()
        return createFuelEntryFormStateHolder(
            scope = scope,
            vehicleId = vehicleId,
            entryId = entryId,
            initialDateEpochMillis = dependencies.clock.now().toEpochMilliseconds(),
            initialOdometerKm =
                vehicleRuntime.repository
                    .observeVehicle(EntityId(vehicleId))
                    .fuelEntryOdometerSuggestions(),
            // TODO(E1-10): Replace the locale-derived creation currency with persisted settings.
            initialCurrencyCode = initialFuelEntryCurrency().value,
            repository = fuelRepository,
            dispatchers = dependencies.dispatchers,
        )
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
        databaseHandle.close()
    }

    private fun checkOpen() {
        check(!closed) { "AppGraph is closed" }
    }

    private fun initialFuelEntryCurrency(): CurrencyCode {
        val suggested = dependencies.localeProvider.current().suggestedCurrency
        return if (
            suggested.value in SUPPORTED_CURRENCY_CODES &&
            MinorUnits.factorFor(suggested) != null
        ) {
            suggested
        } else {
            CurrencyCode("EUR")
        }
    }
}

internal fun Flow<Outcome<Vehicle?, AppError>>.fuelEntryOdometerSuggestions(): Flow<Long> =
    transform { result ->
        if (result is Outcome.Ok) result.value?.let { vehicle -> emit(vehicle.currentOdometerKm) }
    }
