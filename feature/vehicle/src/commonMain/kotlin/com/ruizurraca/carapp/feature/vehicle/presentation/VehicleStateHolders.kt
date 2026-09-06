@file:OptIn(
    kotlin.experimental.ExperimentalObjCName::class,
    kotlin.experimental.ExperimentalObjCRefinement::class,
)

package com.ruizurraca.carapp.feature.vehicle.presentation

import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.Confirmation
import com.ruizurraca.carapp.core.common.DispatcherProvider
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.OwnerContext
import com.ruizurraca.carapp.core.common.STATE_HOLDER_TIMEOUT_MS
import com.ruizurraca.carapp.core.common.SyncStatus
import com.ruizurraca.carapp.core.common.UiMessage
import com.ruizurraca.carapp.core.common.UiMessageKind
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.FuelType
import com.ruizurraca.carapp.core.model.OwnerId
import com.ruizurraca.carapp.core.model.Vehicle
import com.ruizurraca.carapp.feature.vehicle.domain.CreateVehicleCommand
import com.ruizurraca.carapp.feature.vehicle.domain.UpdateVehicleCommand
import com.ruizurraca.carapp.feature.vehicle.domain.VehicleEditFacts
import com.ruizurraca.carapp.feature.vehicle.domain.VehicleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import kotlin.native.HiddenFromObjC
import kotlin.native.ObjCName

@ObjCName(name = "SharedVehicleListStateHolder", swiftName = "VehicleListStateHolder", exact = true)
class VehicleListStateHolder internal constructor(
    scope: CoroutineScope,
    private val repository: VehicleRepository,
    private val dispatchers: DispatcherProvider,
    private val refreshVehicles: suspend () -> Outcome<Unit, AppError>,
    ownerContext: OwnerContext,
) {
    private val holderJob = SupervisorJob(scope.coroutineContext[Job])
    private val holderScope = CoroutineScope(scope.coroutineContext + holderJob)
    private val refreshing = MutableStateFlow(false)
    private var closed = false

    private var observedOwner: OwnerId? = null
    private var observationJob: Job? = null
    private var listing: Outcome<List<Vehicle>, AppError>? = null
    private var selection: String? = null
    private var message: UiMessage? = null

    // `isLoading` means the vehicle list of the currently resolved owner is not known yet. It stays
    // true until that owner publishes a successful result, an owner transition reopens it, and an
    // unreadable list keeps it open while publishing the error (D-116, D-120, CONTRACTS.md 20.10).
    // A refresh over an already known list MUST NOT reopen it, because hosts gate first-run routing
    // on this value. These stay plain comments: KDoc on an exported declaration is written into the
    // Objective-C golden header, and a documentation-only diff there would be noise in a signal
    // reserved for ABI changes.
    private val mutableState = MutableStateFlow(unresolvedState())
    val state: StateFlow<VehicleListUiState> = mutableState

    // Owner resolution is observed undispatched and unconfined so the list becomes unknown inside
    // the same call stack that changes the authentication state. Any other observer of that state,
    // including SessionStateHolder, therefore cannot expose a new session while this holder still
    // publishes the previous owner's list (D-120).
    private val ownerJob =
        holderScope.launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            ownerContext.observe().collect(::onOwnerResolved)
        }

    fun refresh() {
        if (closed || refreshing.value) return
        // A list that could not be read is retried by creating a new local observation. A list that
        // is already known is never reopened, which is what D-116 protects.
        if (listing is Outcome.Err) startObservation()
        refreshing.value = true
        holderScope.launch(dispatchers.main) {
            val result = withContext(dispatchers.io) { refreshVehicles() }
            if (result is Outcome.Err) publish(message = result.error.toErrorMessage())
            refreshing.value = false
        }
    }

    fun selectVehicle(vehicleId: String?) {
        if (closed) return
        publish(selection = vehicleId)
    }

    fun requestDelete(vehicleId: String) {
        if (closed) return
        publish(
            selection = vehicleId,
            message =
                UiMessage(
                    id = DELETE_MESSAGE_ID,
                    kind = UiMessageKind.WARNING,
                    code = DELETE_CONFIRMATION_CODE,
                    confirmation = null,
                ),
        )
    }

    fun confirmDelete(vehicleId: String) {
        if (closed) return
        publish(message = null)
        holderScope.launch(dispatchers.main) {
            when (val result = withContext(dispatchers.io) { repository.deleteVehicle(EntityId(vehicleId)) }) {
                is Outcome.Ok -> if (selection == vehicleId) publish(selection = null)
                is Outcome.Err -> publish(message = result.error.toErrorMessage())
            }
        }
    }

    fun clearMessage() {
        if (closed) return
        publish(message = null)
    }

    fun close() {
        if (closed) return
        closed = true
        observationJob = null
        ownerJob.cancel()
        holderScope.cancel()
    }

    private fun onOwnerResolved(owner: OwnerId) {
        if (closed || owner == observedOwner) return
        observedOwner = owner
        // Nothing owner-scoped survives a session boundary: not the list, not the selection and not
        // the message that described the previous owner's data.
        listing = null
        selection = null
        message = null
        publishCurrent()
        startObservation()
    }

    private fun startObservation() {
        observationJob?.cancel()
        observationJob =
            holderScope.launch(dispatchers.main) {
                repository
                    .observeVehicles(includeDeleted = false)
                    .flowOn(dispatchers.io)
                    .collect { result ->
                        listing = result
                        publishCurrent()
                    }
            }
    }

    private fun publish(
        selection: String? = this.selection,
        message: UiMessage? = this.message,
    ) {
        this.selection = selection
        this.message = message
        publishCurrent()
    }

    private fun publishCurrent() {
        val result = listing
        val readError = (result as? Outcome.Err)?.error
        mutableState.value =
            VehicleListUiState(
                // An unresolved owner and an unreadable list are both "not known", and neither is a
                // confirmed empty list that may open first-vehicle creation.
                isLoading = result == null || readError != null,
                vehicles =
                    (result as? Outcome.Ok)
                        ?.value
                        ?.filter { vehicle -> vehicle.deletedAt == null }
                        ?.map { vehicle ->
                            VehicleListItemUi(
                                id = vehicle.id.value,
                                name = vehicle.name,
                                currentOdometerKm = vehicle.currentOdometerKm,
                                fuelType = vehicle.fuelType,
                                deleted = false,
                            )
                        }.orEmpty(),
                selectedVehicleId = selection,
                syncStatus = SyncStatus.Idle,
                message = message ?: readError?.toErrorMessage(),
            )
    }

    private fun unresolvedState(): VehicleListUiState =
        VehicleListUiState(
            isLoading = true,
            vehicles = emptyList(),
            selectedVehicleId = null,
            syncStatus = SyncStatus.Idle,
            message = null,
        )
}

@ObjCName(name = "SharedVehicleFormStateHolder", swiftName = "VehicleFormStateHolder", exact = true)
class VehicleFormStateHolder internal constructor(
    scope: CoroutineScope,
    vehicleId: String?,
    private val repository: VehicleRepository,
    private val dispatchers: DispatcherProvider,
    private val createVehicle: suspend (CreateVehicleCommand) -> Outcome<EntityId, AppError>,
    private val updateVehicle: suspend (UpdateVehicleCommand) -> Outcome<Unit, AppError>,
) {
    private val holderJob = SupervisorJob(scope.coroutineContext[Job])
    private val holderScope = CoroutineScope(scope.coroutineContext + holderJob)
    private val inputs = MutableStateFlow(FormInputs(vehicleId = vehicleId))
    private val savedVehicleId = MutableStateFlow<String?>(null)
    private val canEditInitialOdometer = MutableStateFlow(true)
    private val saving = MutableStateFlow(false)
    private val transientMessage = MutableStateFlow<UiMessage?>(null)
    private var loadedInitialOdometerKm: Long? = null
    private var loadedFacts = false
    private val editedFields = mutableSetOf<FormField>()
    private var closed = false

    init {
        if (vehicleId != null) {
            holderScope.launch(dispatchers.main) {
                repository
                    .observeVehicleEditFacts(EntityId(vehicleId))
                    .flowOn(dispatchers.io)
                    .collect { result -> applyEditFacts(result) }
            }
        }
    }

    val state: StateFlow<VehicleFormUiState> =
        combine(
            inputs,
            savedVehicleId,
            canEditInitialOdometer,
            saving,
            transientMessage,
        ) { input, savedId, canEdit, isSaving, message ->
            VehicleFormUiState(
                vehicleId = input.vehicleId,
                savedVehicleId = savedId,
                name = input.name,
                initialOdometerKm = input.initialOdometerKm,
                brand = input.brand,
                model = input.model,
                fuelType = input.fuelType,
                canEditInitialOdometer = canEdit,
                isSaving = isSaving,
                message = message,
            )
        }.stateIn(
            scope = holderScope + dispatchers.main,
            started = SharingStarted.WhileSubscribed(STATE_HOLDER_TIMEOUT_MS),
            initialValue = inputs.value.toUiState(),
        )

    fun setName(value: String) {
        editInput(FormField.NAME) { copy(name = value) }
    }

    fun setInitialOdometerKm(value: Long) {
        if (!canEditInitialOdometer.value) return
        editInput(FormField.INITIAL_ODOMETER) { copy(initialOdometerKm = value) }
    }

    fun setBrand(value: String?) {
        editInput(FormField.BRAND) { copy(brand = value) }
    }

    fun setModel(value: String?) {
        editInput(FormField.MODEL) { copy(model = value) }
    }

    fun setFuelType(value: FuelType) {
        editInput(FormField.FUEL_TYPE) { copy(fuelType = value) }
    }

    fun save() {
        if (closed || saving.value) return
        val snapshot = inputs.value
        saving.value = true
        savedVehicleId.value = null
        transientMessage.value = null
        holderScope.launch(dispatchers.main) {
            val result =
                withContext(dispatchers.io) {
                    if (snapshot.vehicleId == null) {
                        createVehicle(snapshot.toCreateCommand())
                    } else {
                        updateVehicle(
                            snapshot.toUpdateCommand(loadedInitialOdometerKm),
                        ).map { EntityId(snapshot.vehicleId) }
                    }
                }
            when (result) {
                is Outcome.Ok -> {
                    if (snapshot.vehicleId == null) inputs.value = FormInputs(vehicleId = null)
                    savedVehicleId.value = result.value.value
                }

                is Outcome.Err -> {
                    transientMessage.value = result.error.toErrorMessage()
                }
            }
            saving.value = false
        }
    }

    fun clearMessage() {
        if (closed) return
        transientMessage.value = null
    }

    fun close() {
        if (closed) return
        closed = true
        holderScope.cancel()
    }

    private fun editInput(
        field: FormField,
        transform: FormInputs.() -> FormInputs,
    ) {
        if (closed) return
        editedFields += field
        inputs.value = inputs.value.transform()
    }

    private fun applyEditFacts(result: Outcome<VehicleEditFacts?, AppError>) {
        when (result) {
            is Outcome.Err -> {
                transientMessage.value = result.error.toErrorMessage()
            }

            is Outcome.Ok -> {
                val facts = result.value
                if (facts == null) {
                    transientMessage.value = ENTITY_NOT_FOUND_MESSAGE
                    return
                }
                if (!loadedFacts) {
                    loadedFacts = true
                    loadedInitialOdometerKm = facts.vehicle.initialOdometerKm
                    val currentInputs = inputs.value
                    inputs.value =
                        FormInputs(
                            vehicleId = facts.vehicle.id.value,
                            name = if (FormField.NAME in editedFields) currentInputs.name else facts.vehicle.name,
                            initialOdometerKm =
                                if (FormField.INITIAL_ODOMETER in editedFields) {
                                    currentInputs.initialOdometerKm
                                } else {
                                    facts.vehicle.initialOdometerKm
                                },
                            brand = if (FormField.BRAND in editedFields) currentInputs.brand else facts.vehicle.brand,
                            model = if (FormField.MODEL in editedFields) currentInputs.model else facts.vehicle.model,
                            fuelType =
                                if (FormField.FUEL_TYPE in editedFields) {
                                    currentInputs.fuelType
                                } else {
                                    facts.vehicle.fuelType
                                },
                        )
                }
                canEditInitialOdometer.value = facts.canEditInitialOdometer
            }
        }
    }
}

private enum class FormField {
    NAME,
    INITIAL_ODOMETER,
    BRAND,
    MODEL,
    FUEL_TYPE,
}

@HiddenFromObjC
fun createVehicleListStateHolder(
    scope: CoroutineScope,
    repository: VehicleRepository,
    dispatchers: DispatcherProvider,
    refreshVehicles: suspend () -> Outcome<Unit, AppError>,
    ownerContext: OwnerContext,
): VehicleListStateHolder = VehicleListStateHolder(scope, repository, dispatchers, refreshVehicles, ownerContext)

@HiddenFromObjC
fun createVehicleFormStateHolder(
    scope: CoroutineScope,
    vehicleId: String?,
    repository: VehicleRepository,
    dispatchers: DispatcherProvider,
    createVehicle: suspend (CreateVehicleCommand) -> Outcome<EntityId, AppError>,
    updateVehicle: suspend (UpdateVehicleCommand) -> Outcome<Unit, AppError>,
): VehicleFormStateHolder =
    VehicleFormStateHolder(
        scope = scope,
        vehicleId = vehicleId,
        repository = repository,
        dispatchers = dispatchers,
        createVehicle = createVehicle,
        updateVehicle = updateVehicle,
    )

private data class FormInputs(
    val vehicleId: String?,
    val name: String = "",
    val initialOdometerKm: Long = 0,
    val brand: String? = null,
    val model: String? = null,
    val fuelType: FuelType = FuelType.GASOLINE,
)

private fun FormInputs.toUiState(): VehicleFormUiState =
    VehicleFormUiState(
        vehicleId = vehicleId,
        savedVehicleId = null,
        name = name,
        initialOdometerKm = initialOdometerKm,
        brand = brand,
        model = model,
        fuelType = fuelType,
        canEditInitialOdometer = true,
        isSaving = false,
        message = null,
    )

private fun FormInputs.toCreateCommand(): CreateVehicleCommand =
    CreateVehicleCommand(
        name = name,
        initialOdometerKm = initialOdometerKm,
        brand = brand,
        model = model,
        fuelType = fuelType,
        confirmations = emptySet<Confirmation>(),
    )

private fun FormInputs.toUpdateCommand(loadedInitialOdometerKm: Long?): UpdateVehicleCommand =
    UpdateVehicleCommand(
        id = EntityId(requireNotNull(vehicleId)),
        name = name,
        initialOdometerKm = initialOdometerKm.takeIf { value -> value != loadedInitialOdometerKm },
        brand = brand,
        model = model,
        fuelType = fuelType,
        confirmations = emptySet<Confirmation>(),
    )

private fun AppError.toErrorMessage(): UiMessage =
    UiMessage(
        id = ERROR_MESSAGE_ID,
        kind = UiMessageKind.ERROR,
        code = code,
        confirmation = null,
    )

private fun <T, E> Outcome<T, E>.map(transform: (T) -> EntityId): Outcome<EntityId, E> =
    when (this) {
        is Outcome.Ok -> Outcome.Ok(transform(value))
        is Outcome.Err -> Outcome.Err(error)
    }

private val ENTITY_NOT_FOUND_MESSAGE =
    UiMessage(
        id = ERROR_MESSAGE_ID,
        kind = UiMessageKind.ERROR,
        code = "VALIDATION.ENTITY_NOT_FOUND",
        confirmation = null,
    )

private const val ERROR_MESSAGE_ID = 1L
private const val DELETE_MESSAGE_ID = 2L
private const val DELETE_CONFIRMATION_CODE = "INFO.CONFIRM_DELETE_VEHICLE"
