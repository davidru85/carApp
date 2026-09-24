@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.auth.AuthState
import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.LogLevel
import com.ruizurraca.carapp.core.common.MinorUnits
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.SyncTrigger
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
import com.ruizurraca.carapp.core.model.LOCAL_OWNER
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
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

    /**
     * Releases the graph, then suspends until the `DatabaseHandle` has actually been closed.
     *
     * `close()` returns as soon as the scope is cancelled: the handle is released by a bounded waiter
     * on another coroutine, because releasing it before graph-owned work has drained is the `D-172`
     * hazard. A caller that needs the file to be genuinely free - deleting it, for instance - MUST
     * await this instead of assuming `close()` finished the release.
     */
    suspend fun awaitClosed()
}

internal class DefaultAppGraph(
    internal val dependencies: AppGraphDependencies,
) : AppGraph {
    @Volatile
    private var closed = false

    // Completed by whichever path releases the handle, so `awaitClosed()` can observe the release
    // rather than assume it.
    private val closeCompletion = CompletableDeferred<Unit>()
    private val graphScope = CoroutineScope(SupervisorJob() + dependencies.dispatchers.io)
    private val databaseHandle = dependencies.databaseFactory.create()

    // `D-188`: every owner-scoped component must observe the coordinated owner, not the raw delegate.
    // The coordinator publishes an owner only after counting the recovery it causes, which is what
    // closes the interval in which a resolved empty list could otherwise escape. It reports cycles to
    // the controller in `init`, once that controller exists.
    private val ownerRecoveryGate = OwnerRecoveryGate(dependencies.ownerContext)
    private val ownerAwareDependencies = dependencies.copy(ownerContext = ownerRecoveryGate)

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
    private val localOwnerAdoption = LocalOwnerAdoption(ownerAwareDependencies, databaseHandle.database)
    private val syncController =
        createSyncController(
            scope = graphScope,
            databaseAccess = SyncDatabaseAccess(databaseHandle.database),
            ownerContext = ownerAwareDependencies.ownerContext,
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
        VehicleSliceRuntime(ownerAwareDependencies, databaseHandle.database, localOwnerAdoption, syncController)
    private val fuelRepository: FuelEntryRepository =
        SyncRequestingFuelEntryRepository(
            delegate =
                AdoptionNotifyingFuelEntryRepository(
                    delegate =
                        SqlDelightFuelEntryRepository(
                            databaseAccess = FuelEntryDatabaseAccess(databaseHandle.database),
                            ownerContext = ownerAwareDependencies.ownerContext,
                            clock = dependencies.clock,
                            uuidGenerator = dependencies.uuidGenerator,
                        ),
                    adoption = localOwnerAdoption,
                ),
            syncController = syncController,
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
        observeConnectivityRecovery()
        ownerRecoveryGate.launchIn(graphScope, syncController)
        arrangePeriodicScheduling()
    }

    /**
     * Hands the `§9.8` `Periodic` trigger to the platform scheduler through the injected
     * `SyncTriggerAdapter` (`§20.10`).
     *
     * This is the one trigger that genuinely needs a platform scheduler: a 6-hour cadence survives
     * process death only as `WorkManager` unique periodic work or a `BGAppRefreshTask`, and neither is
     * reachable from shared code. `PostWriteDebounce` and `ConnectivityRecovered` are deliberately
     * *not* routed here: both are in-process events the graph already observes directly (a commit and
     * a connectivity edge), so sending them to a platform scheduler would add latency and duplicate a
     * trigger that is already exact.
     *
     * Arranged once per graph. The scheduler is asked to *hold* the cadence, not to fire a cycle now;
     * when the platform fires, its worker calls `requestSync(Periodic)` on this same controller, which
     * is what keeps `§9.1`'s single in-process controller authoritative.
     */
    private fun arrangePeriodicScheduling() {
        dependencies.syncTriggerAdapter.schedule(SyncTrigger.Periodic)
    }

    /**
     * Fires the `§9.8` `ConnectivityRecovered` trigger on the offline-to-online edge.
     *
     * The trigger is the *transition*, not the value: a device that is already online produces no
     * recovery, so the observer's current value is the baseline and only a later change can trigger.
     * That is also why the trigger is derived here rather than in the controller: only the graph owns
     * the observer, and the engine must not depend on a platform signal to decide its own admission
     * (`§9.1`).
     *
     * Collection starts undispatched so the baseline is read synchronously inside construction. With a
     * plain `launch` the collector could subscribe after the device had already recovered, `drop(1)`
     * would discard that recovery as if it were the baseline, and the trigger would be lost until the
     * next connectivity change - which for a device that stays online is never.
     *
     * The cycle is requested, never awaited, so the collector keeps observing.
     * `ConnectivityRecovered` is also the reason-dependent step that makes connectivity-only failures
     * due again (`§9.7`).
     */
    private fun observeConnectivityRecovery() {
        graphScope.launch(start = CoroutineStart.UNDISPATCHED) {
            dependencies.connectivityObserver.isOnline
                .drop(1)
                .collect { online ->
                    if (online) syncController.requestSync(SyncTrigger.ConnectivityRecovered)
                }
        }
    }

    override fun vehicleListStateHolder(scope: CoroutineScope): VehicleListStateHolder {
        checkOpen()
        return createVehicleListStateHolder(
            scope = scope,
            repository = vehicleRuntime.repository,
            dispatchers = dependencies.dispatchers,
            refreshVehicles = vehicleRuntime::refresh,
            ownerContext = ownerAwareDependencies.ownerContext,
            syncStatus = syncController.status,
            recoveryOutstanding = ownerRecoveryGate.outstanding,
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
            releaseDatabase()
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
            releaseDatabase()
        }
    }

    /**
     * The single release path, so the handle is closed once and `awaitClosed()` observes it.
     *
     * `close()` is idempotent, so the backstop and the join can both reach the waiter; only the first
     * one closes and completes, which keeps the `D-89` "at most once" rule.
     */
    private fun releaseDatabase() {
        databaseHandle.close()
        closeCompletion.complete(Unit)
    }

    override suspend fun awaitClosed() {
        close()
        closeCompletion.await()
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
