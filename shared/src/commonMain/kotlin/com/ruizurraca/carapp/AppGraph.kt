@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.auth.AuthState
import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.LogLevel
import com.ruizurraca.carapp.core.common.MinorUnits
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.resolveLocaleCurrency
import com.ruizurraca.carapp.core.database.AccountConversionDatabaseAccess
import com.ruizurraca.carapp.core.database.AccountDepartureDatabaseAccess
import com.ruizurraca.carapp.core.database.AnonymousReminderDatabaseAccess
import com.ruizurraca.carapp.core.database.FuelEntryDatabaseAccess
import com.ruizurraca.carapp.core.database.LocalDataClearDatabaseAccess
import com.ruizurraca.carapp.core.database.SettingsDatabaseAccess
import com.ruizurraca.carapp.core.database.SyncDatabaseAccess
import com.ruizurraca.carapp.core.model.CurrencyCode
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.UserSettings
import com.ruizurraca.carapp.core.model.Vehicle
import com.ruizurraca.carapp.core.sync.SyncController
import com.ruizurraca.carapp.core.sync.createSyncController
import com.ruizurraca.carapp.feature.fuel.data.SqlDelightFuelEntryRepository
import com.ruizurraca.carapp.feature.fuel.domain.FuelEntryRepository
import com.ruizurraca.carapp.feature.fuel.presentation.FuelEntryFormStateHolder
import com.ruizurraca.carapp.feature.fuel.presentation.FuelEntryListStateHolder
import com.ruizurraca.carapp.feature.fuel.presentation.createFuelEntryFormStateHolder
import com.ruizurraca.carapp.feature.fuel.presentation.createFuelEntryListStateHolder
import com.ruizurraca.carapp.feature.session.data.SqlDelightAnonymousReminderRepository
import com.ruizurraca.carapp.feature.session.data.SqlDelightSettingsRepository
import com.ruizurraca.carapp.feature.vehicle.presentation.VehicleFormStateHolder
import com.ruizurraca.carapp.feature.vehicle.presentation.VehicleListStateHolder
import com.ruizurraca.carapp.feature.vehicle.presentation.createVehicleFormStateHolder
import com.ruizurraca.carapp.feature.vehicle.presentation.createVehicleListStateHolder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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

    fun syncStateHolder(scope: CoroutineScope): SyncStateHolder

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
    private val accountConversion =
        AccountConversionCoordinator(
            authClient = dependencies.authClient,
            orphanCleanupClient = dependencies.orphanCleanupClient,
            remoteSyncSource = dependencies.remoteSyncSource,
            store = AccountConversionDatabaseAccess(databaseHandle.database),
            clock = dependencies.clock,
        )
    private val accountDeparture =
        AccountDepartureCoordinator(
            databaseAccess = LocalDataClearDatabaseAccess(databaseHandle.database),
            departureAccess = AccountDepartureDatabaseAccess(databaseHandle.database),
            authClient = dependencies.authClient,
        )
    private val localOwnerAdoption = LocalOwnerAdoption(dependencies, databaseHandle.database)
    private val syncController =
        createSyncController(
            scope = graphScope,
            databaseAccess = SyncDatabaseAccess(databaseHandle.database),
            ownerContext = dependencies.ownerContext,
            connectivity = dependencies.connectivityObserver,
            remote = dependencies.remoteSyncSource,
            clock = dependencies.clock,
            uuidGenerator = dependencies.uuidGenerator,
            adoption = localOwnerAdoption::awaitAdoption,
            onPoisoned = dependencies.crashReporter::recordNonFatal,
            onQuarantined = { record ->
                dependencies.logger.log(
                    level = LogLevel.WARN,
                    tag = "Sync",
                    message = "Remote document quarantined",
                    fields =
                        mapOf(
                            "entityType" to record.entityType.name,
                            "code" to "SYNC.Quarantined",
                            "schemaVersion" to record.schemaVersion.toString(),
                        ),
                    throwable = null,
                )
            },
            isDebugBuild = dependencies.isDebugBuild,
        )
    private val vehicleRuntime =
        VehicleSliceRuntime(dependencies, databaseHandle.database, localOwnerAdoption, syncController)
    private val fuelRepository: FuelEntryRepository =
        AdoptionNotifyingFuelEntryRepository(
            delegate =
                SqlDelightFuelEntryRepository(
                    databaseAccess = FuelEntryDatabaseAccess(databaseHandle.database),
                    ownerContext = dependencies.ownerContext,
                    clock = dependencies.clock,
                    uuidGenerator = dependencies.uuidGenerator,
                ),
            adoption = localOwnerAdoption,
        )
    private val anonymousReminders =
        SqlDelightAnonymousReminderRepository(
            AnonymousReminderDatabaseAccess(databaseHandle.database),
        )
    private val settingsRepository =
        SqlDelightSettingsRepository(
            databaseAccess = SettingsDatabaseAccess(databaseHandle.database),
            localeProvider = dependencies.localeProvider,
            canCreateDefaults = { !closed },
        )

    init {
        // Keep these eager launches after every property they touch. Adoption is automatic by
        // contract (§11.2, §11.4): nothing in the UI starts it.
        graphScope.launch { bootstrapSettings() }
        // `D-167`: a departure interrupted by a process death is finished at the next launch, before
        // anything can observe local data belonging to an account that is already gone.
        graphScope.launch { accountDeparture.resumePending() }
        graphScope.launch {
            dependencies.authClient.authState
                .filterIsInstance<AuthState.SignedIn>()
                .collect { state ->
                    if (!state.session.isAnonymous) accountConversion.resumePending()
                }
        }
        localOwnerAdoption.launchIn(graphScope)
    }

    override fun vehicleListStateHolder(scope: CoroutineScope): VehicleListStateHolder {
        checkOpen()
        return createVehicleListStateHolder(
            scope = scope,
            repository = vehicleRuntime.repository,
            dispatchers = dependencies.dispatchers,
            refreshVehicles = vehicleRuntime::refresh,
            ownerContext = dependencies.ownerContext,
            syncStatus = syncController.status,
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
            syncStatus = syncController.status,
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
        return SessionStateHolder(
            scope = scope,
            authClient = dependencies.authClient,
            onLocalStartAccepted = localOwnerAdoption::onLocalStartAccepted,
            clock = dependencies.clock,
            anonymousReminders = anonymousReminders,
            accountConversion = accountConversion,
            analyticsTracker = dependencies.analyticsTracker,
            accountDeparture = accountDeparture,
        )
    }

    override fun syncStateHolder(scope: CoroutineScope): SyncStateHolder {
        checkOpen()
        return SyncStateHolder(
            scope = scope,
            controller = syncController,
            connectivity = dependencies.connectivityObserver,
            dispatchers = dependencies.dispatchers,
        )
    }

    override fun syncController(): SyncController {
        checkOpen()
        return syncController
    }

    override fun close() {
        if (closed) return
        closed = true
        // Refuse new cycles and release every in-flight `sync()` awaiter before the scope is cancelled:
        // a caller of `sync()` lives outside `graphScope`, so cancelling that scope would leave it
        // suspended on a deferred nothing else completes (`D-172`).
        syncController.shutdown()
        // The auth client is not the `D-172` hazard - the driver is - and closing it synchronously is
        // the established contract, so it does not wait for graph work to drain.
        (dependencies.authClient as? AutoCloseable)?.close()
        val job = graphScope.coroutineContext[Job]
        graphScope.cancel()
        if (job == null) {
            databaseHandle.close()
            return
        }
        // The handle must not be released while graph-owned work is still running, and `cancel()` does
        // not join. One bounded waiter, on a scope that outlives the graph, is therefore the single
        // place that releases it: it joins the cancelled scope and releases afterwards, or gives up at
        // the deadline if the work never observes cancellation, so the handle can never be held for the
        // life of the process. A single writer keeps the `D-89` "at most once" rule without needing a
        // lock, which common code has no synchronous form of.
        CoroutineScope(SupervisorJob() + dependencies.dispatchers.io).launch {
            withTimeoutOrNull(RELEASE_BACKSTOP_MILLIS) { job.join() }
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

/**
 * How long `close()` waits for graph-owned work before releasing the `DatabaseHandle` anyway (`D-172`).
 * A cooperative cycle unwinds during `cancel()` and releases the handle immediately, so this only
 * bounds the residual case of work that ignores cancellation. `docs/SECURITY.md` records the window.
 */
private const val RELEASE_BACKSTOP_MILLIS = 5_000L

internal fun Flow<Outcome<Vehicle?, AppError>>.fuelEntryOdometerSuggestions(): Flow<Long> =
    transform { result ->
        if (result is Outcome.Ok) result.value?.let { vehicle -> emit(vehicle.currentOdometerKm) }
    }

internal fun Flow<Outcome<UserSettings, AppError>>.currencyCodes(): Flow<String> =
    transform { result ->
        if (result is Outcome.Ok) emit(result.value.currency.value)
    }
