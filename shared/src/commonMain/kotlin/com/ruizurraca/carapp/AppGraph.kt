@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.MinorUnits
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.resolveLocaleCurrency
import com.ruizurraca.carapp.core.database.FuelEntryDatabaseAccess
import com.ruizurraca.carapp.core.database.SettingsDatabaseAccess
import com.ruizurraca.carapp.core.model.CurrencyCode
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.UserSettings
import com.ruizurraca.carapp.core.model.Vehicle
import com.ruizurraca.carapp.core.sync.SyncController
import com.ruizurraca.carapp.feature.fuel.data.SqlDelightFuelEntryRepository
import com.ruizurraca.carapp.feature.fuel.presentation.FuelEntryFormStateHolder
import com.ruizurraca.carapp.feature.fuel.presentation.FuelEntryListStateHolder
import com.ruizurraca.carapp.feature.fuel.presentation.createFuelEntryFormStateHolder
import com.ruizurraca.carapp.feature.fuel.presentation.createFuelEntryListStateHolder
import com.ruizurraca.carapp.feature.session.data.SqlDelightSettingsRepository
import com.ruizurraca.carapp.feature.vehicle.presentation.VehicleFormStateHolder
import com.ruizurraca.carapp.feature.vehicle.presentation.VehicleListStateHolder
import com.ruizurraca.carapp.feature.vehicle.presentation.createVehicleFormStateHolder
import com.ruizurraca.carapp.feature.vehicle.presentation.createVehicleListStateHolder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile
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
    @Volatile
    private var closed = false
    private val graphScope = CoroutineScope(SupervisorJob() + dependencies.dispatchers.io)
    private val databaseHandle = dependencies.databaseFactory.create()
    private val vehicleRuntime = VehicleSliceRuntime(dependencies, databaseHandle.database)
    private val fuelRepository =
        SqlDelightFuelEntryRepository(
            databaseAccess = FuelEntryDatabaseAccess(databaseHandle.database),
            ownerContext = dependencies.ownerContext,
            clock = dependencies.clock,
            uuidGenerator = dependencies.uuidGenerator,
        )
    private val settingsRepository =
        SqlDelightSettingsRepository(
            databaseAccess = SettingsDatabaseAccess(databaseHandle.database),
            localeProvider = dependencies.localeProvider,
            canCreateDefaults = { !closed },
        )

    init {
        // Keep this eager launch after every property touched by bootstrapSettings().
        graphScope.launch { bootstrapSettings() }
    }

    override fun vehicleListStateHolder(scope: CoroutineScope): VehicleListStateHolder {
        checkOpen()
        return createVehicleListStateHolder(
            scope = scope,
            repository = vehicleRuntime.repository,
            dispatchers = dependencies.dispatchers,
            refreshVehicles = vehicleRuntime::refresh,
            ownerContext = dependencies.ownerContext,
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
            initialCurrencyCode = initialFuelEntryCurrency().value,
            settingsCurrencyCode = settingsRepository.settings.currencyCodes(),
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
        graphScope.cancel()
        try {
            (dependencies.authClient as? AutoCloseable)?.close()
        } finally {
            databaseHandle.close()
        }
    }

    private fun checkOpen() {
        check(!closed) { "AppGraph is closed" }
    }

    private fun initialFuelEntryCurrency(): CurrencyCode {
        val suggested = dependencies.localeProvider.current().suggestedCurrency
        return resolveLocaleCurrency(
            suggestedCurrency = suggested,
            // This only re-checks the supported set; host adapters validate real runtime minor units.
            runtimeMinorUnitFactor = MinorUnits.factorFor(suggested),
        )
    }

    private suspend fun bootstrapSettings() {
        try {
            settingsRepository.settings.first { result -> result is Outcome.Ok }
        } catch (_: CancellationException) {
            // Closing the graph intentionally terminates this best-effort accelerator.
        } catch (_: Throwable) {
            // D-106 keeps bootstrap failure silent; repository access remains self-healing.
        }
    }
}

internal fun Flow<Outcome<Vehicle?, AppError>>.fuelEntryOdometerSuggestions(): Flow<Long> =
    transform { result ->
        if (result is Outcome.Ok) result.value?.let { vehicle -> emit(vehicle.currentOdometerKm) }
    }

internal fun Flow<Outcome<UserSettings, AppError>>.currencyCodes(): Flow<String> =
    transform { result ->
        if (result is Outcome.Ok) emit(result.value.currency.value)
    }
