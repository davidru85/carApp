package com.ruizurraca.carapp

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ruizurraca.carapp.core.common.UiMessage
import com.ruizurraca.carapp.core.model.FuelType
import com.ruizurraca.carapp.feature.fuel.presentation.FuelEntryFormStateHolder
import com.ruizurraca.carapp.feature.fuel.presentation.FuelEntryListStateHolder
import com.ruizurraca.carapp.feature.fuel.presentation.FuelEntryListUiState
import com.ruizurraca.carapp.feature.vehicle.domain.INITIAL_ODOMETER_RANGE_KM
import com.ruizurraca.carapp.feature.vehicle.presentation.VehicleFormStateHolder
import com.ruizurraca.carapp.feature.vehicle.presentation.VehicleFormUiState
import com.ruizurraca.carapp.feature.vehicle.presentation.VehicleListItemUi
import com.ruizurraca.carapp.feature.vehicle.presentation.VehicleListStateHolder
import com.ruizurraca.carapp.feature.vehicle.presentation.VehicleListUiState
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewModel =
            ViewModelProvider(
                owner = this,
                factory = VehicleAppViewModel.factory(application),
            )[VehicleAppViewModel::class.java]
        val credentialSource = AndroidGoogleCredentialSource(this)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    VehicleApp(
                        viewModel = viewModel,
                        acquireGoogleCredential = credentialSource::acquire,
                    )
                }
            }
        }
    }
}

internal class VehicleAppViewModel(
    application: Application,
) : ViewModel() {
    // The graph is process-scoped (§9.1): a WorkManager worker must reach the same SyncController,
    // and this ViewModel only consumes it. `onCleared` therefore releases this Activity's own holders
    // and deliberately does not close the graph, which outlives every Activity of the process.
    private val graph = AndroidAppGraph.require()
    val isDebugBuild = application.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    val sessionStateHolder: SessionStateHolder = graph.sessionStateHolder(scope = viewModelScope)
    val vehicleListStateHolder: VehicleListStateHolder = graph.vehicleListStateHolder(scope = viewModelScope)
    val syncStateHolder: SyncStateHolder = graph.syncStateHolder(scope = viewModelScope)
    val settingsStateHolder: SettingsStateHolder = graph.settingsStateHolder(scope = viewModelScope)
    private val formStateHolders = mutableMapOf<String, VehicleFormStateHolder>()
    private val fuelEntryListStateHolders = mutableMapOf<String, FuelEntryListStateHolder>()
    private val fuelEntryFormStateHolders = mutableMapOf<Pair<String, String?>, FuelEntryFormStateHolder>()

    fun vehicleFormStateHolder(vehicleId: String?): VehicleFormStateHolder =
        formStateHolders.getOrPut(vehicleId.cacheKey()) {
            graph.vehicleFormStateHolder(scope = viewModelScope, vehicleId = vehicleId)
        }

    fun closeVehicleForm(vehicleId: String?) {
        formStateHolders.remove(vehicleId.cacheKey())?.close()
    }

    fun fuelEntryListStateHolder(vehicleId: String): FuelEntryListStateHolder =
        fuelEntryListStateHolders.getOrPut(vehicleId) {
            graph.fuelEntryListStateHolder(scope = viewModelScope, vehicleId = vehicleId)
        }

    fun closeFuelEntryList(vehicleId: String) {
        fuelEntryListStateHolders.remove(vehicleId)?.close()
    }

    fun fuelEntryFormStateHolder(
        vehicleId: String,
        entryId: String?,
    ): FuelEntryFormStateHolder =
        fuelEntryFormStateHolders.getOrPut(vehicleId to entryId) {
            graph.fuelEntryFormStateHolder(scope = viewModelScope, vehicleId = vehicleId, entryId = entryId)
        }

    fun closeFuelEntryForm(
        vehicleId: String,
        entryId: String?,
    ) {
        fuelEntryFormStateHolders.remove(vehicleId to entryId)?.close()
    }

    override fun onCleared() {
        formStateHolders.values.forEach(VehicleFormStateHolder::close)
        fuelEntryListStateHolders.values.forEach(FuelEntryListStateHolder::close)
        fuelEntryFormStateHolders.values.forEach(FuelEntryFormStateHolder::close)
        formStateHolders.clear()
        fuelEntryListStateHolders.clear()
        fuelEntryFormStateHolders.clear()
        sessionStateHolder.close()
        vehicleListStateHolder.close()
        syncStateHolder.close()
        settingsStateHolder.close()
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = VehicleAppViewModel(application) as T
            }
    }
}

@Composable
private fun VehicleApp(
    viewModel: VehicleAppViewModel,
    acquireGoogleCredential: suspend () -> GoogleCredentialAcquisition,
) {
    val sessionState by viewModel.sessionStateHolder.state.collectAsState()
    val vehicleState by viewModel.vehicleListStateHolder.state.collectAsState()
    val googleSignIn = rememberGoogleSignIn(viewModel.sessionStateHolder, acquireGoogleCredential)
    val reauthenticationInFlight =
        rememberReauthenticationInFlight(viewModel.sessionStateHolder, sessionState.isBusy)

    // Launch and foreground return are the only evaluation moments of the D-62 schedule (§11.3).
    // The same edge is the §9.8 AppForeground trigger: the holder applies the background-duration
    // threshold, so the host only reports the fact it observed.
    OnForegroundReturn { backgroundMillis ->
        viewModel.sessionStateHolder.evaluateAnonymousReminder()
        viewModel.syncStateHolder.onForegroundReturn(backgroundMillis)
    }

    // B1-D4: a departure owns the whole window while it runs, retains a step, hands over to the local
    // clear or waits for re-authentication, because the authenticated `NavHost` is not its surface.
    when (resolveRootDestination(sessionState, vehicleState.vehicles.size, reauthenticationInFlight.value)) {
        OnboardingDestination.WAITING -> {
            WaitingIndicator()
        }

        OnboardingDestination.WELCOME -> {
            WelcomeScreen(
                state = sessionState,
                onGoogle = googleSignIn.signIn,
                onContinueWithoutAccount = viewModel.sessionStateHolder::startAnonymousSignIn,
            )
        }

        OnboardingDestination.DEPARTURE -> {
            DepartureRoute(
                session = sessionState,
                sessionStateHolder = viewModel.sessionStateHolder,
                onReauthenticateWithGoogle = {
                    reauthenticationInFlight.value = true
                    googleSignIn.reauthenticate()
                },
            )
        }

        OnboardingDestination.FIRST_VEHICLE,
        OnboardingDestination.VEHICLE_LIST,
        -> {
            AuthenticatedApp(
                viewModel = viewModel,
                vehicleState = vehicleState,
                anonymousReminderIndex = sessionState.anonymousReminderIndex,
                googleSignIn = googleSignIn,
            )
            // A failed deletion re-mounts the `NavHost` at the vehicle list, so the departure prompts
            // live here, above it, instead of inside the Settings route.
            SettingsSessionPrompts(
                session = sessionState,
                callbacks =
                    sessionPromptCallbacks(
                        session = viewModel.sessionStateHolder,
                        sync = viewModel.syncStateHolder,
                        phase = sessionState.phase,
                    ),
            )
        }
    }
}

/**
 * The authenticated graph is mounted once and is never rebuilt from owner state, so navigation
 * survives the vehicle list becoming known, empty or non-empty. `VehicleRoutes.LIST` is always the
 * start destination and first-run creation is pushed over it, which keeps every back stack exit and
 * the post-save route to the vehicle detail valid for the first vehicle as well as for later ones.
 */
@Composable
private fun AuthenticatedApp(
    viewModel: VehicleAppViewModel,
    vehicleState: VehicleListUiState,
    anonymousReminderIndex: Int?,
    googleSignIn: GoogleSignInActions,
) {
    val navController = rememberNavController()
    var firstVehicleCreationPresented by rememberSaveable { mutableStateOf(false) }
    val gate = vehicleListGate(isLoading = vehicleState.isLoading, hasMessage = vehicleState.message != null)
    var previousGate by rememberSaveable { mutableStateOf(gate) }

    Column(modifier = Modifier.fillMaxSize()) {
        // The notice sits above the product surface instead of over it, so it never gates a feature.
        anonymousReminderIndex?.let { index ->
            AnonymousReminderBanner(
                index = index,
                onDismiss = viewModel.sessionStateHolder::dismissAnonymousReminder,
            )
        }
        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = VehicleRoutes.LIST,
            ) {
                vehicleRoutes(navController, viewModel)
                fuelEntryRoutes(navController, viewModel)
                settingsRoute(navController, viewModel, googleSignIn)
            }
            when (gate) {
                VehicleListGate.WAITING -> {
                    WaitingIndicator()
                }

                VehicleListGate.UNREADABLE -> {
                    UnreadableVehicleList(onRetry = viewModel.vehicleListStateHolder::refresh)
                }

                VehicleListGate.RESOLVED -> {
                    Unit
                }
            }
        }
    }

    LaunchedEffect(gate, vehicleState.vehicles.size) {
        if (shouldResetOwnerScopedNavigation(gate = gate, previousGate = previousGate)) {
            // The list no longer belongs to the session that built this navigation.
            firstVehicleCreationPresented = false
            navController.popBackStack(VehicleRoutes.LIST, inclusive = false)
        }
        previousGate = gate

        val presentFirstVehicleCreation =
            shouldPresentFirstVehicleCreation(
                isVehicleListKnown = gate == VehicleListGate.RESOLVED,
                vehicleCount = vehicleState.vehicles.size,
                alreadyPresented = firstVehicleCreationPresented,
            )
        if (presentFirstVehicleCreation) {
            firstVehicleCreationPresented = true
            navController.navigate(VehicleRoutes.CREATE_FIRST)
        }
    }
}

/**
 * A list that could not be read is reported and retried, never hidden behind an indefinite
 * indicator. The message itself is localized from the shared `UiMessage` code.
 */
@Composable
private fun UnreadableVehicleList(onRetry: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.vehicle_list_unreadable),
                modifier = Modifier.testTag(VehicleTestTags.LIST_UNREADABLE).announcedError(),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onRetry,
                modifier = Modifier.testTag(VehicleTestTags.LIST_RETRY),
            ) {
                Text(stringResource(R.string.retry))
            }
        }
    }
}

/**
 * Native credential acquisition is bound to the host that renders it, so a configuration change can
 * cancel it before any completion intent runs. The in-flight marker survives that recreation and
 * abandons the orphaned attempt, which is what keeps the welcome screen usable.
 */
@Composable
private fun rememberGoogleSignIn(
    sessionStateHolder: SessionStateHolder,
    acquireGoogleCredential: suspend () -> GoogleCredentialAcquisition,
): GoogleSignInActions {
    val scope = rememberCoroutineScope()
    var acquisitionInFlight by rememberSaveable { mutableStateOf(false) }
    val coordinator =
        remember(sessionStateHolder, acquireGoogleCredential) {
            AndroidGoogleSignInCoordinator(
                acquireCredential = acquireGoogleCredential,
                startPermanentSignIn = sessionStateHolder::startPermanentSignIn,
                startAccountConversion = sessionStateHolder::startAccountConversion,
                completeGoogleSignIn = sessionStateHolder::completeGoogleSignIn,
                failSignIn = sessionStateHolder::failSignIn,
                setAcquisitionInFlight = { inFlight -> acquisitionInFlight = inFlight },
            )
        }

    LaunchedEffect(coordinator) {
        if (acquisitionInFlight) coordinator.abandonInterruptedAcquisition()
    }

    return GoogleSignInActions(
        signIn = { scope.launch { coordinator.signIn() } },
        reauthenticate = {
            // Undispatched, so `startReauthentication` has made the session busy before this returns:
            // the host's in-flight flag must never observe its own start as an already idle session.
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                coordinator.acquireAndComplete(sessionStateHolder::startReauthentication)
            }
        },
        // `E4-05`: the same native picker path, entered through the shared account-conversion intent.
        // Undispatched for the same reason as `reauthenticate`.
        convert = {
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                coordinator.convert()
            }
        },
    )
}

@Composable
private fun WaitingIndicator() {
    Surface(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LabelledProgressIndicator()
        }
    }
}

private fun NavGraphBuilder.vehicleRoutes(
    navController: NavHostController,
    viewModel: VehicleAppViewModel,
) {
    composable(VehicleRoutes.LIST) {
        VehicleListScreen(
            stateHolder = viewModel.vehicleListStateHolder,
            syncStateHolder = viewModel.syncStateHolder,
            onCreate = { navController.navigate(VehicleRoutes.CREATE) },
            onOpen = { vehicleId -> navController.navigate(VehicleRoutes.detail(vehicleId)) },
            showDiagnostics = viewModel.isDebugBuild,
            onDiagnostics = { navController.navigate(VehicleRoutes.SYNC_DIAGNOSTICS) },
            onSettings = { navController.navigate(VehicleRoutes.SETTINGS) },
        )
    }
    composable(VehicleRoutes.SYNC_DIAGNOSTICS) {
        SyncDiagnosticsScreen(
            stateHolder = viewModel.syncStateHolder,
            onBack = { navController.popBackStack() },
        )
    }
    vehicleCreationRoute(
        route = VehicleRoutes.CREATE,
        offersBackAffordance = true,
        navController = navController,
        viewModel = viewModel,
    )
    vehicleCreationRoute(
        route = VehicleRoutes.CREATE_FIRST,
        offersBackAffordance = false,
        navController = navController,
        viewModel = viewModel,
    )
    composable(
        route = VehicleRoutes.EDIT,
        arguments = listOf(navArgument(VehicleRoutes.VEHICLE_ID) { type = NavType.StringType }),
    ) { entry ->
        val vehicleId = checkNotNull(entry.arguments?.getString(VehicleRoutes.VEHICLE_ID))
        val stateHolder =
            remember(entry) {
                viewModel.vehicleFormStateHolder(vehicleId)
            }
        ReleaseHolderOnBackStackExit(entry) {
            viewModel.closeVehicleForm(vehicleId)
        }
        VehicleFormScreen(
            stateHolder = stateHolder,
            originalVehicleId = vehicleId,
            onBack = navController::popBackStack,
            onSaved = { navController.popBackStack() },
        )
    }
    composable(
        route = VehicleRoutes.DETAIL,
        arguments = listOf(navArgument(VehicleRoutes.VEHICLE_ID) { type = NavType.StringType }),
    ) { entry ->
        VehicleDetailRoute(entry, navController, viewModel)
    }
}

/**
 * F-1 first-run creation is the same form without a way back: the owner is already signed in, so the
 * only forward step is creating the vehicle. The route identifies that context explicitly instead of
 * reading the live vehicle count, which stops being zero the moment the vehicle is saved.
 */
private fun NavGraphBuilder.vehicleCreationRoute(
    route: String,
    offersBackAffordance: Boolean,
    navController: NavHostController,
    viewModel: VehicleAppViewModel,
) {
    composable(route) { entry ->
        val stateHolder =
            remember(entry) {
                viewModel.vehicleFormStateHolder(vehicleId = null)
            }
        ReleaseHolderOnBackStackExit(entry) {
            viewModel.closeVehicleForm(vehicleId = null)
        }
        // First-run creation is mandatory, so it also consumes the system and predictive back
        // gestures. Every other creation and edit route keeps the platform behaviour.
        BackHandler(enabled = !offersBackAffordance) {}
        VehicleFormScreen(
            stateHolder = stateHolder,
            originalVehicleId = null,
            onBack = if (offersBackAffordance) ({ navController.popBackStack() }) else null,
            onSaved = { vehicleId ->
                navController.navigate(VehicleRoutes.detail(vehicleId)) {
                    popUpTo(route) { inclusive = true }
                }
            },
        )
    }
}

private fun NavGraphBuilder.fuelEntryRoutes(
    navController: NavHostController,
    viewModel: VehicleAppViewModel,
) {
    composable(
        route = VehicleRoutes.FUEL_CREATE,
        arguments = listOf(navArgument(VehicleRoutes.VEHICLE_ID) { type = NavType.StringType }),
    ) { entry ->
        val vehicleId = checkNotNull(entry.arguments?.getString(VehicleRoutes.VEHICLE_ID))
        val stateHolder = remember(entry) { viewModel.fuelEntryFormStateHolder(vehicleId, entryId = null) }
        ReleaseHolderOnBackStackExit(entry) { viewModel.closeFuelEntryForm(vehicleId, entryId = null) }
        FuelEntryFormScreen(
            stateHolder = stateHolder,
            onBack = navController::popBackStack,
            onSaved = { navController.popBackStack() },
        )
    }
    composable(
        route = VehicleRoutes.FUEL_EDIT,
        arguments =
            listOf(
                navArgument(VehicleRoutes.VEHICLE_ID) { type = NavType.StringType },
                navArgument(VehicleRoutes.ENTRY_ID) { type = NavType.StringType },
            ),
    ) { entry ->
        val vehicleId = checkNotNull(entry.arguments?.getString(VehicleRoutes.VEHICLE_ID))
        val entryId = checkNotNull(entry.arguments?.getString(VehicleRoutes.ENTRY_ID))
        val stateHolder = remember(entry) { viewModel.fuelEntryFormStateHolder(vehicleId, entryId) }
        ReleaseHolderOnBackStackExit(entry) { viewModel.closeFuelEntryForm(vehicleId, entryId) }
        FuelEntryFormScreen(
            stateHolder = stateHolder,
            onBack = navController::popBackStack,
            onSaved = { navController.popBackStack() },
        )
    }
}

@Composable
private fun VehicleDetailRoute(
    entry: NavBackStackEntry,
    navController: NavHostController,
    viewModel: VehicleAppViewModel,
) {
    val vehicleId = checkNotNull(entry.arguments?.getString(VehicleRoutes.VEHICLE_ID))
    val fuelEntryListStateHolder = remember(entry) { viewModel.fuelEntryListStateHolder(vehicleId) }
    ReleaseHolderOnBackStackExit(entry) { viewModel.closeFuelEntryList(vehicleId) }
    VehicleDetailScreen(
        navController = navController,
        stateHolder = viewModel.vehicleListStateHolder,
        fuelEntryListStateHolder = fuelEntryListStateHolder,
        vehicleId = vehicleId,
    )
}

@Composable
private fun ReleaseHolderOnBackStackExit(
    entry: NavBackStackEntry,
    releaseForm: () -> Unit,
) {
    val activity = LocalContext.current as? ComponentActivity
    DisposableEffect(entry, activity) {
        onDispose {
            if (activity == null) {
                releaseForm()
            } else {
                Handler(Looper.getMainLooper()).post {
                    releaseVehicleFormAfterDisposal(activity, releaseForm)
                }
            }
        }
    }
}

private fun releaseVehicleFormAfterDisposal(
    activity: ComponentActivity,
    releaseForm: () -> Unit,
) {
    when (activity.lifecycle.currentState) {
        Lifecycle.State.RESUMED -> {
            releaseForm()
        }

        Lifecycle.State.DESTROYED -> {
            if (!activity.isChangingConfigurations) releaseForm()
        }

        else -> {
            val observer =
                object : LifecycleEventObserver {
                    override fun onStateChanged(
                        source: LifecycleOwner,
                        event: Lifecycle.Event,
                    ) {
                        if (event != Lifecycle.Event.ON_DESTROY) return
                        source.lifecycle.removeObserver(this)
                        if (!activity.isChangingConfigurations) releaseForm()
                    }
                }
            activity.lifecycle.addObserver(observer)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VehicleListScreen(
    stateHolder: VehicleListStateHolder,
    syncStateHolder: SyncStateHolder,
    onCreate: () -> Unit,
    onOpen: (String) -> Unit,
    showDiagnostics: Boolean,
    onDiagnostics: () -> Unit,
    onSettings: () -> Unit,
) {
    val state by stateHolder.state.collectAsState()
    val syncState by syncStateHolder.state.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.vehicle_list_title), modifier = Modifier.screenHeading()) },
                actions = {
                    TextButton(
                        onClick = onSettings,
                        modifier = Modifier.testTag(SettingsTestTags.ENTRY),
                    ) {
                        Text(stringResource(R.string.settings_title))
                    }
                    if (showDiagnostics) {
                        TextButton(onClick = onDiagnostics) {
                            Text(stringResource(R.string.sync_diagnostics))
                        }
                    }
                    TextButton(
                        onClick = stateHolder::refresh,
                        enabled = vehicleListGate(state.isLoading, state.message != null) != VehicleListGate.WAITING,
                    ) {
                        Text(stringResource(R.string.restore_backup))
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreate,
                modifier = Modifier.testTag(VehicleTestTags.ADD_VEHICLE),
            ) {
                Text(stringResource(R.string.add_vehicle))
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // ADR-0193: the vehicle-list state owns Android status rendering; SyncStateHolder owns
            // retry messages and commands.
            SyncStatusIndicator(
                status = state.syncStatus,
                message = syncState.message,
                onRetry = syncStateHolder::retryFailed,
            )
            VehicleListContent(
                state = state,
                onOpen = onOpen,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SyncDiagnosticsScreen(
    stateHolder: SyncStateHolder,
    onBack: () -> Unit,
) {
    val lines by stateHolder.debugLines.collectAsState()
    LaunchedEffect(stateHolder) { stateHolder.refreshDebug() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sync_diagnostics)) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                },
                actions = {
                    TextButton(onClick = stateHolder::refreshDebug) {
                        Text(stringResource(R.string.refresh_sync_diagnostics))
                    }
                },
            )
        },
    ) { padding ->
        if (lines.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.empty_sync_diagnostics))
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                items(lines) { line -> Text(line, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun VehicleListContent(
    state: VehicleListUiState,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = currentDisplayLocale()
    Box(
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            state.isLoading && state.vehicles.isEmpty() -> {
                LabelledProgressIndicator()
            }

            state.message != null && state.vehicles.isEmpty() -> {
                ErrorText(state.message)
            }

            state.vehicles.isEmpty() -> {
                Text(stringResource(R.string.empty_vehicles))
            }

            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.vehicles, key = VehicleListItemUi::id) { vehicle ->
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpen(vehicle.id) }
                                    .padding(vertical = 16.dp)
                                    .testTag(VehicleTestTags.vehicleRow(vehicle.id)),
                        ) {
                            Text(vehicle.name, style = MaterialTheme.typography.titleMedium)
                            val odometer = formatInteger(vehicle.currentOdometerKm, locale)
                            Text(stringResource(R.string.vehicle_odometer, odometer))
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VehicleFormTopBar(
    titleResource: Int,
    onBack: (() -> Unit)?,
) {
    TopAppBar(
        title = { Text(stringResource(titleResource), modifier = Modifier.screenHeading()) },
        navigationIcon = {
            if (onBack != null) {
                TextButton(
                    onClick = onBack,
                    modifier = Modifier.testTag(VehicleTestTags.BACK),
                ) {
                    Text(stringResource(R.string.back))
                }
            }
        },
    )
}

@Composable
internal fun VehicleFormScreen(
    stateHolder: VehicleFormStateHolder,
    originalVehicleId: String?,
    onBack: (() -> Unit)?,
    onSaved: (String) -> Unit,
) {
    val state by stateHolder.state.collectAsState()
    val name = rememberSaveableFormText(stateHolder, state.name, stateHolder::setName)
    val brand =
        rememberSaveableFormText(stateHolder, state.brand.orEmpty()) { value ->
            stateHolder.setBrand(value.ifBlank { null })
        }
    val model =
        rememberSaveableFormText(stateHolder, state.model.orEmpty()) { value ->
            stateHolder.setModel(value.ifBlank { null })
        }
    val odometer =
        rememberSaveableOdometerInput(
            key = stateHolder,
            sharedValue = state.initialOdometerKm,
            publish = stateHolder::setInitialOdometerKm,
        )
    val fuelType = rememberSaveableFuelType(stateHolder, state.fuelType, stateHolder::setFuelType)
    val titleResource = if (originalVehicleId == null) R.string.create_vehicle_title else R.string.edit_vehicle_title
    LaunchedEffect(state.savedVehicleId, state.isSaving) {
        val savedVehicleId = state.savedVehicleId
        if (originalVehicleId == null && savedVehicleId != null && !state.isSaving) onSaved(savedVehicleId)
    }

    Scaffold(
        topBar = { VehicleFormTopBar(titleResource = titleResource, onBack = onBack) },
    ) { padding ->
        VehicleForm(
            state =
                state.copy(
                    name = name.value,
                    brand = brand.value.ifBlank { null },
                    model = model.value.ifBlank { null },
                    fuelType = fuelType.value,
                ),
            onNameChange = name.onValueChange,
            odometerText = odometer.text,
            hasOdometerError = odometer.hasError,
            onOdometerChange = odometer.onValueChange,
            onBrandChange = { value -> brand.onValueChange(value.orEmpty()) },
            onModelChange = { value -> model.onValueChange(value.orEmpty()) },
            onFuelTypeChange = fuelType.onValueChange,
            onSave = stateHolder::save,
            modifier = Modifier.padding(padding),
        )
    }
}

internal data class SaveableFormText(
    val value: String,
    val onValueChange: (String) -> Unit,
)

@Composable
internal fun rememberSaveableFormText(
    key: Any,
    sharedValue: String,
    publish: (String) -> Unit,
): SaveableFormText {
    var value by rememberSaveable(key) { mutableStateOf(sharedValue) }
    var edited by rememberSaveable(key) { mutableStateOf(false) }
    LaunchedEffect(sharedValue) {
        if (!edited) value = sharedValue
    }
    LaunchedEffect(edited, value, sharedValue) {
        if (edited && value != sharedValue) publish(value)
    }
    return SaveableFormText(value) { updatedValue ->
        edited = true
        value = updatedValue
        publish(updatedValue)
    }
}

private data class SaveableFuelType(
    val value: FuelType,
    val onValueChange: (FuelType) -> Unit,
)

/**
 * The fuel type is part of the saveable draft exactly like the text fields: a selection survives
 * saved-instance-state restoration and is republished into a fresh holder before save (`D-127`).
 */
@Composable
private fun rememberSaveableFuelType(
    key: Any,
    sharedValue: FuelType,
    publish: (FuelType) -> Unit,
): SaveableFuelType {
    var value by rememberSaveable(key) { mutableStateOf(sharedValue) }
    var edited by rememberSaveable(key) { mutableStateOf(false) }
    LaunchedEffect(sharedValue) {
        if (!edited) value = sharedValue
    }
    LaunchedEffect(edited, value, sharedValue) {
        if (edited && value != sharedValue) publish(value)
    }
    return SaveableFuelType(value) { updatedValue ->
        edited = true
        value = updatedValue
        publish(updatedValue)
    }
}

private data class SaveableOdometerInput(
    val text: String,
    val hasError: Boolean,
    val onValueChange: (String) -> Unit,
)

@Composable
private fun rememberSaveableOdometerInput(
    key: Any,
    sharedValue: Long,
    publish: (Long) -> Unit,
): SaveableOdometerInput {
    var text by rememberSaveable(key) { mutableStateOf(sharedValue.toString()) }
    var edited by rememberSaveable(key) { mutableStateOf(false) }
    LaunchedEffect(sharedValue) {
        if (!edited) text = sharedValue.toString()
    }
    val value = text.toLongOrNull()?.takeIf { parsed -> parsed in INITIAL_ODOMETER_RANGE_KM }
    LaunchedEffect(edited, value, sharedValue) {
        if (edited && value != null && value != sharedValue) publish(value)
    }
    return SaveableOdometerInput(
        text = text,
        hasError = edited && value == null,
        onValueChange = { updatedText ->
            edited = true
            text = updatedText
            updatedText.toLongOrNull()?.takeIf { parsed -> parsed in INITIAL_ODOMETER_RANGE_KM }?.let(publish)
        },
    )
}

@Composable
private fun VehicleForm(
    state: VehicleFormUiState,
    onNameChange: (String) -> Unit,
    odometerText: String,
    hasOdometerError: Boolean,
    onOdometerChange: (String) -> Unit,
    onBrandChange: (String?) -> Unit,
    onModelChange: (String?) -> Unit,
    onFuelTypeChange: (FuelType) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        // `E4-02`: every field and the save action stay reachable at a 200% font scale.
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        VehicleTextFields(
            state = state,
            odometerText = odometerText,
            hasOdometerError = hasOdometerError,
            onNameChange = onNameChange,
            onOdometerChange = onOdometerChange,
            onBrandChange = onBrandChange,
            onModelChange = onModelChange,
        )
        FuelTypeSelector(
            selected = state.fuelType,
            onSelect = onFuelTypeChange,
        )
        if (hasOdometerError) {
            Text(
                text = stringResource(R.string.error_out_of_range),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag(VehicleTestTags.ERROR).announcedError(),
            )
        } else {
            state.message?.let { ErrorText(it) }
        }
        Button(
            onClick = onSave,
            enabled = !state.isSaving && !hasOdometerError,
            modifier = Modifier.fillMaxWidth().testTag(VehicleTestTags.SAVE),
        ) {
            if (state.isSaving) {
                LabelledProgressIndicator(labelResource = R.string.saving)
            } else {
                Text(stringResource(R.string.save_vehicle))
            }
        }
    }
}

@Composable
private fun VehicleTextFields(
    state: VehicleFormUiState,
    odometerText: String,
    hasOdometerError: Boolean,
    onNameChange: (String) -> Unit,
    onOdometerChange: (String) -> Unit,
    onBrandChange: (String?) -> Unit,
    onModelChange: (String?) -> Unit,
) {
    OutlinedTextField(
        value = state.name,
        onValueChange = onNameChange,
        modifier = Modifier.fillMaxWidth().testTag(VehicleTestTags.NAME),
        label = { Text(stringResource(R.string.vehicle_name)) },
        singleLine = true,
    )
    OutlinedTextField(
        value = odometerText,
        onValueChange = onOdometerChange,
        modifier = Modifier.fillMaxWidth().testTag(VehicleTestTags.ODOMETER),
        enabled = state.canEditInitialOdometer,
        isError = hasOdometerError,
        label = { Text(stringResource(R.string.initial_odometer)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
    )
    OutlinedTextField(
        value = state.brand.orEmpty(),
        onValueChange = { value -> onBrandChange(value.ifBlank { null }) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.vehicle_brand)) },
        singleLine = true,
    )
    OutlinedTextField(
        value = state.model.orEmpty(),
        onValueChange = { value -> onModelChange(value.ifBlank { null }) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.vehicle_model)) },
        singleLine = true,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FuelTypeSelector(
    selected: FuelType,
    onSelect: (FuelType) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = stringResource(selected.labelResource()),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.fuel_type)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(VehicleTestTags.FUEL_TYPE_INPUT)
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            FuelType.entries.forEach { fuelType ->
                DropdownMenuItem(
                    text = { Text(stringResource(fuelType.labelResource())) },
                    onClick = {
                        expanded = false
                        onSelect(fuelType)
                    },
                    modifier = Modifier.testTag(VehicleTestTags.FUEL_TYPE_OPTION),
                )
            }
        }
    }
}

/**
 * The display label of each of the five MVP `FuelType` values (`D-127`). The `when` is exhaustive
 * over the enum, so a value added later (`ELECTRIC` and `HYBRID` belong to `E5-01`) fails
 * compilation here instead of crashing the form at runtime.
 */
private fun FuelType.labelResource(): Int =
    when (this) {
        FuelType.GASOLINE -> R.string.fuel_type_gasoline
        FuelType.DIESEL -> R.string.fuel_type_diesel
        FuelType.LPG -> R.string.fuel_type_lpg
        FuelType.CNG -> R.string.fuel_type_cng
        FuelType.OTHER -> R.string.fuel_type_other
    }

@Composable
private fun VehicleDetailScreen(
    navController: NavHostController,
    stateHolder: VehicleListStateHolder,
    fuelEntryListStateHolder: FuelEntryListStateHolder,
    vehicleId: String,
) {
    val state by stateHolder.state.collectAsState()
    val fuelState by fuelEntryListStateHolder.state.collectAsState()
    val vehicle = state.vehicles.firstOrNull { item -> item.id == vehicleId }
    val showDeleteDialog = state.selectedVehicleId == vehicleId && state.message?.code == DELETE_CONFIRMATION_CODE

    if (showDeleteDialog) {
        VehicleDeleteConfirmation(
            onDismiss = stateHolder::clearMessage,
            onConfirm = {
                stateHolder.confirmDelete(vehicleId)
                navController.popBackStack(VehicleRoutes.LIST, inclusive = false)
            },
        )
    }

    VehicleDetailLayout(
        vehicle = vehicle,
        isLoading = state.isLoading,
        fuelState = fuelState,
        onBack = { navController.popBackStack() },
        onAddFuelEntry = { navController.navigate(VehicleRoutes.fuelCreate(vehicleId)) },
        onEdit = { navController.navigate(VehicleRoutes.edit(vehicleId)) },
        onDelete = { stateHolder.requestDelete(vehicleId) },
        onEditEntry = { entryId -> navController.navigate(VehicleRoutes.fuelEdit(vehicleId, entryId)) },
    )
}

/**
 * The stateless vehicle detail (`E4-02`). The header, the consumption summary and the entries are one
 * scrolling list, so at a 200% font scale nothing is pushed off the screen, and the two actions are
 * full-width buttons stacked vertically instead of a row that cannot fit both labels.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VehicleDetailLayout(
    vehicle: VehicleListItemUi?,
    isLoading: Boolean,
    fuelState: FuelEntryListUiState,
    onBack: () -> Unit,
    onAddFuelEntry: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onEditEntry: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.vehicle_detail_title), modifier = Modifier.screenHeading()) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddFuelEntry,
                modifier = Modifier.testTag(FuelEntryTestTags.ADD_FUEL_ENTRY),
            ) {
                Text(stringResource(R.string.add_fuel_entry))
            }
        },
    ) { padding ->
        val contentModifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)
        when {
            isLoading && vehicle == null -> {
                Box(modifier = contentModifier, contentAlignment = Alignment.Center) {
                    LabelledProgressIndicator()
                }
            }

            vehicle == null -> {
                Text(stringResource(R.string.vehicle_not_found), modifier = contentModifier.padding(top = 20.dp))
            }

            else -> {
                FuelEntryListContent(
                    state = fuelState,
                    onEdit = onEditEntry,
                    modifier = contentModifier,
                    header = { VehicleDetailHeader(vehicle = vehicle, onEdit = onEdit, onDelete = onDelete) },
                )
            }
        }
    }
}

@Composable
private fun VehicleDetailHeader(
    vehicle: VehicleListItemUi,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val locale = currentDisplayLocale()
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = vehicle.name,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.testTag(VehicleTestTags.DETAIL_NAME).screenHeading(),
        )
        Text(stringResource(R.string.vehicle_odometer, formatInteger(vehicle.currentOdometerKm, locale)))
        Button(
            onClick = onEdit,
            modifier = Modifier.fillMaxWidth().testTag(VehicleTestTags.EDIT),
        ) {
            Text(stringResource(R.string.edit_vehicle))
        }
        OutlinedButton(
            onClick = onDelete,
            modifier = Modifier.fillMaxWidth().testTag(VehicleTestTags.DELETE),
        ) {
            Text(stringResource(R.string.delete_vehicle))
        }
    }
}

/** The vehicle deletion confirmation; its body scrolls at a 200% font scale (`E4-02`). */
@Composable
internal fun VehicleDeleteConfirmation(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_vehicle_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.delete_vehicle_confirmation))
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag(VehicleTestTags.CONFIRM_DELETE_VEHICLE),
            ) {
                Text(stringResource(R.string.delete))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag(VehicleTestTags.CANCEL_DELETE_VEHICLE),
            ) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
internal fun ErrorText(
    message: UiMessage?,
    testTag: String = VehicleTestTags.ERROR,
) {
    if (message == null) return
    Text(
        text = stringResource(message.stringResource()),
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.testTag(testTag).announcedError(),
    )
}

private fun String?.cacheKey(): String = this ?: CREATE_FORM_CACHE_KEY

internal object VehicleRoutes {
    const val VEHICLE_ID = "vehicleId"
    const val ENTRY_ID = "entryId"
    const val LIST = "vehicles"
    const val CREATE = "vehicles/create"
    const val CREATE_FIRST = "vehicles/create/first"
    const val EDIT = "vehicles/edit/{$VEHICLE_ID}"
    const val DETAIL = "vehicles/detail/{$VEHICLE_ID}"
    const val FUEL_CREATE = "vehicles/{$VEHICLE_ID}/fuel/create"
    const val FUEL_EDIT = "vehicles/{$VEHICLE_ID}/fuel/{$ENTRY_ID}/edit"
    const val SYNC_DIAGNOSTICS = "debug/sync"
    const val SETTINGS = "settings"

    fun edit(vehicleId: String): String = "vehicles/edit/$vehicleId"

    fun detail(vehicleId: String): String = "vehicles/detail/$vehicleId"

    fun fuelCreate(vehicleId: String): String = "vehicles/$vehicleId/fuel/create"

    fun fuelEdit(
        vehicleId: String,
        entryId: String,
    ): String = "vehicles/$vehicleId/fuel/$entryId/edit"
}

object VehicleTestTags {
    const val ADD_VEHICLE = "add_vehicle"
    const val NAME = "vehicle_name"
    const val ODOMETER = "vehicle_odometer"
    const val SAVE = "save_vehicle"
    const val BACK = "vehicle_form_back"
    const val LIST_UNREADABLE = "vehicle_list_unreadable"
    const val LIST_RETRY = "vehicle_list_retry"
    const val DETAIL_NAME = "vehicle_detail_name"
    const val FIRST_FUEL_INVITATION = "first_fuel_invitation"
    const val ERROR = "vehicle_error"
    const val FUEL_TYPE_INPUT = "fuel_type_input"
    const val FUEL_TYPE_OPTION = "fuel_type_option"
    const val EDIT = "vehicle_detail_edit"
    const val DELETE = "vehicle_detail_delete"
    const val CONFIRM_DELETE_VEHICLE = "confirm_delete_vehicle"
    const val CANCEL_DELETE_VEHICLE = "cancel_delete_vehicle"

    fun vehicleRow(vehicleId: String): String = "vehicle_row_$vehicleId"
}

internal const val DATABASE_FILE_NAME = "carapp.db"
private const val CREATE_FORM_CACHE_KEY = "__create__"
private const val DELETE_CONFIRMATION_CODE = "INFO.CONFIRM_DELETE_VEHICLE"
