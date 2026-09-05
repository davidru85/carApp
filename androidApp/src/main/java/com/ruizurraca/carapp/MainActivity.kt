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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.ruizurraca.carapp.feature.fuel.presentation.FuelEntryFormStateHolder
import com.ruizurraca.carapp.feature.fuel.presentation.FuelEntryListStateHolder
import com.ruizurraca.carapp.feature.fuel.presentation.FuelEntryListUiState
import com.ruizurraca.carapp.feature.vehicle.domain.INITIAL_ODOMETER_RANGE_KM
import com.ruizurraca.carapp.feature.vehicle.presentation.VehicleFormStateHolder
import com.ruizurraca.carapp.feature.vehicle.presentation.VehicleFormUiState
import com.ruizurraca.carapp.feature.vehicle.presentation.VehicleListItemUi
import com.ruizurraca.carapp.feature.vehicle.presentation.VehicleListStateHolder
import com.ruizurraca.carapp.feature.vehicle.presentation.VehicleListUiState
import com.ruizurraca.carapp.wiring.firebase.firebaseAppProviders
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
    private val providers =
        firebaseAppProviders(
            databaseFilePath = application.getDatabasePath(DATABASE_FILE_NAME).absolutePath,
            localeProvider = AndroidLocaleProvider(),
        )
    private val isDebugBuild = application.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    private val graph = buildAppGraph(isDebugBuild = isDebugBuild, providers = providers)
    val sessionStateHolder: SessionStateHolder = graph.sessionStateHolder(scope = viewModelScope)
    val vehicleListStateHolder: VehicleListStateHolder = graph.vehicleListStateHolder(scope = viewModelScope)
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
        graph.close()
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
    val onGoogle = rememberGoogleSignIn(viewModel.sessionStateHolder, acquireGoogleCredential)

    when (resolveOnboardingDestination(sessionState.phase, vehicleState.vehicles.size)) {
        OnboardingDestination.WAITING -> {
            WaitingIndicator()
        }

        OnboardingDestination.WELCOME -> {
            WelcomeScreen(
                state = sessionState,
                onGoogle = onGoogle,
                onContinueWithoutAccount = viewModel.sessionStateHolder::startAnonymousSignIn,
            )
        }

        OnboardingDestination.FIRST_VEHICLE,
        OnboardingDestination.VEHICLE_LIST,
        -> {
            AuthenticatedApp(viewModel = viewModel, vehicleState = vehicleState)
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
) {
    val navController = rememberNavController()
    var firstVehicleCreationPresented by rememberSaveable { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = VehicleRoutes.LIST,
        ) {
            vehicleRoutes(navController, viewModel)
            fuelEntryRoutes(navController, viewModel)
        }
        if (vehicleState.isLoading) WaitingIndicator()
    }

    LaunchedEffect(vehicleState.isLoading, vehicleState.vehicles.size) {
        val presentFirstVehicleCreation =
            shouldPresentFirstVehicleCreation(
                isVehicleListKnown = !vehicleState.isLoading,
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
 * Native credential acquisition is bound to the host that renders it, so a configuration change can
 * cancel it before any completion intent runs. The in-flight marker survives that recreation and
 * abandons the orphaned attempt, which is what keeps the welcome screen usable.
 */
@Composable
private fun rememberGoogleSignIn(
    sessionStateHolder: SessionStateHolder,
    acquireGoogleCredential: suspend () -> GoogleCredentialAcquisition,
): () -> Unit {
    val scope = rememberCoroutineScope()
    var acquisitionInFlight by rememberSaveable { mutableStateOf(false) }
    val coordinator =
        remember(sessionStateHolder, acquireGoogleCredential) {
            AndroidGoogleSignInCoordinator(
                acquireCredential = acquireGoogleCredential,
                startPermanentSignIn = sessionStateHolder::startPermanentSignIn,
                completeGoogleSignIn = sessionStateHolder::completeGoogleSignIn,
                failSignIn = sessionStateHolder::failSignIn,
                setAcquisitionInFlight = { inFlight -> acquisitionInFlight = inFlight },
            )
        }

    LaunchedEffect(coordinator) {
        if (acquisitionInFlight) coordinator.abandonInterruptedAcquisition()
    }

    return { scope.launch { coordinator.signIn() } }
}

@Composable
private fun WaitingIndicator() {
    Surface(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
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
            onCreate = { navController.navigate(VehicleRoutes.CREATE) },
            onOpen = { vehicleId -> navController.navigate(VehicleRoutes.detail(vehicleId)) },
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
    onCreate: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val state by stateHolder.state.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.vehicle_list_title)) },
                actions = {
                    TextButton(
                        onClick = stateHolder::refresh,
                        enabled = !state.isLoading,
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
        VehicleListContent(
            state = state,
            onOpen = onOpen,
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
private fun VehicleListContent(
    state: VehicleListUiState,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            state.isLoading && state.vehicles.isEmpty() -> {
                CircularProgressIndicator()
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
                            Text(stringResource(R.string.vehicle_odometer, vehicle.currentOdometerKm))
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
        title = { Text(stringResource(titleResource)) },
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
                ),
            onNameChange = name.onValueChange,
            odometerText = odometer.text,
            hasOdometerError = odometer.hasError,
            onOdometerChange = odometer.onValueChange,
            onBrandChange = { value -> brand.onValueChange(value.orEmpty()) },
            onModelChange = { value -> model.onValueChange(value.orEmpty()) },
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
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
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
        if (hasOdometerError) {
            Text(
                text = stringResource(R.string.error_out_of_range),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag(VehicleTestTags.ERROR),
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
                CircularProgressIndicator()
            } else {
                Text(stringResource(R.string.save_vehicle))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.vehicle_detail_title)) },
                navigationIcon = {
                    TextButton(onClick = navController::popBackStack) { Text(stringResource(R.string.back)) }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { navController.navigate(VehicleRoutes.fuelCreate(vehicleId)) },
                modifier = Modifier.testTag(FuelEntryTestTags.ADD_FUEL_ENTRY),
            ) {
                Text(stringResource(R.string.add_fuel_entry))
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when {
                state.isLoading && vehicle == null -> {
                    CircularProgressIndicator()
                }

                vehicle == null -> {
                    Text(stringResource(R.string.vehicle_not_found))
                }

                else -> {
                    VehicleDetailContent(
                        vehicle = vehicle,
                        fuelState = fuelState,
                        navController = navController,
                        stateHolder = stateHolder,
                        fuelListModifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun VehicleDeleteConfirmation(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_vehicle_title)) },
        text = { Text(stringResource(R.string.delete_vehicle_confirmation)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun VehicleDetailContent(
    vehicle: VehicleListItemUi,
    fuelState: FuelEntryListUiState,
    navController: NavHostController,
    stateHolder: VehicleListStateHolder,
    fuelListModifier: Modifier,
) {
    Text(
        text = vehicle.name,
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.testTag(VehicleTestTags.DETAIL_NAME),
    )
    Text(stringResource(R.string.vehicle_odometer, vehicle.currentOdometerKm))
    Row {
        Button(onClick = { navController.navigate(VehicleRoutes.edit(vehicle.id)) }) {
            Text(stringResource(R.string.edit_vehicle))
        }
        Spacer(Modifier.width(12.dp))
        OutlinedButton(onClick = { stateHolder.requestDelete(vehicle.id) }) {
            Text(stringResource(R.string.delete_vehicle))
        }
    }
    FuelEntryListContent(
        state = fuelState,
        onEdit = { entryId -> navController.navigate(VehicleRoutes.fuelEdit(vehicle.id, entryId)) },
        modifier = fuelListModifier,
    )
}

@Composable
internal fun ErrorText(message: UiMessage?) {
    if (message == null) return
    Text(
        text = stringResource(message.stringResource()),
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.testTag(VehicleTestTags.ERROR),
    )
}

internal fun UiMessage.stringResource(): Int =
    if (code.startsWith(AUTH_ERROR_PREFIX)) {
        authStringResource(code)
    } else {
        nonAuthStringResource(code)
    }

private fun nonAuthStringResource(code: String): Int =
    when (code) {
        "VALIDATION.REQUIRED_FIELD" -> R.string.error_required_field

        "VALIDATION.INVALID_LENGTH" -> R.string.error_invalid_length

        "VALIDATION.OUT_OF_RANGE" -> R.string.error_out_of_range

        "VALIDATION.EDIT_NOT_ALLOWED" -> R.string.error_edit_not_allowed

        "VALIDATION.DUPLICATE_NAME" -> R.string.error_duplicate_vehicle_name

        "VALIDATION.NO_OP" -> R.string.error_no_changes

        "VALIDATION.ENTITY_DELETED" -> R.string.error_vehicle_deleted

        "VALIDATION.ENTITY_NOT_FOUND" -> R.string.vehicle_not_found

        "PERSISTENCE.DATABASE_UNAVAILABLE",
        "PERSISTENCE.TRANSACTION_FAILED",
        "PERSISTENCE.MIGRATION_FAILED",
        "PERSISTENCE.SERIALIZATION_FAILED",
        "PERSISTENCE.CONSTRAINT_VIOLATION",
        -> R.string.error_persistence

        "REMOTE.UNAVAILABLE",
        "REMOTE.DEADLINE_EXCEEDED",
        "REMOTE.PERMISSION_DENIED",
        "REMOTE.UNAUTHENTICATED",
        "REMOTE.INVALID_ARGUMENT",
        "REMOTE.NOT_FOUND",
        "REMOTE.UNKNOWN",
        -> R.string.error_restore

        else -> R.string.error_unexpected
    }

internal fun authStringResource(code: String): Int =
    when (code) {
        "AUTH.CANCELLED" -> R.string.error_auth_cancelled
        "AUTH.NETWORK_UNAVAILABLE" -> R.string.error_auth_network
        "AUTH.PROVIDER_UNAVAILABLE" -> R.string.error_auth_provider
        else -> R.string.error_unexpected
    }

private const val AUTH_ERROR_PREFIX = "AUTH."

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
    const val DETAIL_NAME = "vehicle_detail_name"
    const val FIRST_FUEL_INVITATION = "first_fuel_invitation"
    const val ERROR = "vehicle_error"
    const val FUEL_TYPE_INPUT = "fuel_type_input"

    fun vehicleRow(vehicleId: String): String = "vehicle_row_$vehicleId"
}

internal const val DATABASE_FILE_NAME = "carapp.db"
private const val CREATE_FORM_CACHE_KEY = "__create__"
private const val DELETE_CONFIRMATION_CODE = "INFO.CONFIRM_DELETE_VEHICLE"
