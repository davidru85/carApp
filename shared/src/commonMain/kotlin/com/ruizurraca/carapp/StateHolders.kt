package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.common.AuthProvider
import com.ruizurraca.carapp.core.common.Confirmation
import com.ruizurraca.carapp.core.common.SyncTrigger
import com.ruizurraca.carapp.core.model.FuelType
import com.ruizurraca.carapp.core.sync.SyncStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class VehicleListStateHolder internal constructor(
    private val scope: CoroutineScope? = null,
    vehicles: Flow<List<VehicleListItemUi>>? = null,
    private val refreshVehicles: (suspend () -> Unit)? = null,
) {
    private var closed = false
    private val mutableState =
        MutableStateFlow(VehicleListUiState(false, emptyList(), null, SyncStatus.Idle, null))
    private val observationJob: Job? =
        if (scope != null && vehicles != null) {
            scope.launch {
                vehicles.collect { items ->
                    mutableState.value = mutableState.value.copy(vehicles = items)
                }
            }
        } else {
            null
        }
    val state: StateFlow<VehicleListUiState> = mutableState

    fun refresh() {
        if (closed) return
        val operationScope = scope ?: return
        val operation = refreshVehicles ?: return
        mutableState.value = mutableState.value.copy(isLoading = true)
        operationScope.launch {
            operation()
            mutableState.value = mutableState.value.copy(isLoading = false)
        }
    }

    fun selectVehicle(vehicleId: String?) = vehicleId.let { Unit }

    fun requestDelete(vehicleId: String) = vehicleId.let { Unit }

    fun confirmDelete(vehicleId: String) = vehicleId.let { Unit }

    fun clearMessage() = Unit

    fun close() {
        if (closed) return
        closed = true
        observationJob?.cancel()
    }
}

class VehicleFormStateHolder internal constructor(
    vehicleId: String?,
    private val scope: CoroutineScope? = null,
    private val saveVehicle: (suspend (VehicleFormUiState) -> Unit)? = null,
) {
    private val mutableState =
        MutableStateFlow(
            VehicleFormUiState(
                vehicleId = vehicleId,
                name = "",
                initialOdometerKm = 0L,
                brand = null,
                model = null,
                fuelType = FuelType.GASOLINE,
                canEditInitialOdometer = true,
                isSaving = false,
                message = null,
            ),
        )
    val state: StateFlow<VehicleFormUiState> = mutableState

    fun setName(value: String) {
        mutableState.value = mutableState.value.copy(name = value)
    }

    fun setInitialOdometerKm(value: Long) = value.let { Unit }

    fun setBrand(value: String?) = value.let { Unit }

    fun setModel(value: String?) = value.let { Unit }

    fun setFuelType(value: FuelType) = value.let { Unit }

    fun save() {
        val operation = saveVehicle ?: return
        val operationScope = scope ?: return
        mutableState.value = mutableState.value.copy(isSaving = true)
        operationScope.launch {
            operation(mutableState.value)
            mutableState.value = mutableState.value.copy(isSaving = false)
        }
    }

    fun clearMessage() = Unit

    fun close() = Unit
}

class FuelEntryListStateHolder internal constructor(
    vehicleId: String,
) {
    val state: StateFlow<FuelEntryListUiState> =
        MutableStateFlow(
            FuelEntryListUiState(
                vehicleId = vehicleId,
                isLoading = false,
                entries = emptyList(),
                consumptionAverageScaled = null,
                validConsumptionSegmentCount = 0,
                isConsumptionReliable = false,
                syncStatus = SyncStatus.Idle,
                message = null,
            ),
        )

    fun refresh() = Unit

    fun requestDelete(entryId: String) = entryId.let { Unit }

    fun confirmDelete(entryId: String) = entryId.let { Unit }

    fun clearMessage() = Unit

    fun close() = Unit
}

class FuelEntryFormStateHolder internal constructor(
    vehicleId: String,
    entryId: String?,
) {
    val state: StateFlow<FuelEntryFormUiState> =
        MutableStateFlow(
            FuelEntryFormUiState(
                vehicleId = vehicleId,
                entryId = entryId,
                dateEpochMillis = 0L,
                odometerKm = 0L,
                moneyInputMode = MoneyInputMode.LITERS_AND_PRICE,
                litersScaled = null,
                pricePerLiterScaled = null,
                totalCostMinor = null,
                currencyCode = "EUR",
                isFullTank = false,
                hasMissedEntries = false,
                notes = null,
                isSaving = false,
                message = null,
            ),
        )

    fun setDateEpochMillis(value: Long) = value.let { Unit }

    fun setOdometerKm(value: Long) = value.let { Unit }

    fun setMoneyInputMode(value: MoneyInputMode) = value.let { Unit }

    fun setLitersScaled(value: Long?) = value.let { Unit }

    fun setPricePerLiterScaled(value: Long?) = value.let { Unit }

    fun setTotalCostMinor(value: Long?) = value.let { Unit }

    fun setCurrencyCode(value: String) = value.let { Unit }

    fun setFullTank(value: Boolean) = value.let { Unit }

    fun setMissedEntries(value: Boolean) = value.let { Unit }

    fun setNotes(value: String?) = value.let { Unit }

    fun save() = Unit

    fun confirmSave(confirmation: Confirmation) = confirmation.let { Unit }

    fun clearMessage() = Unit

    fun close() = Unit
}

class SessionStateHolder internal constructor() {
    val state: StateFlow<SessionUiState> =
        MutableStateFlow(SessionUiState(SessionPhase.UNKNOWN, emptyList(), false, null))

    fun startAnonymousSignIn() = Unit

    fun startPermanentSignIn(provider: AuthProvider) = provider.let { Unit }

    fun startAccountConversion(provider: AuthProvider) = provider.let { Unit }

    fun confirmAccountConversion(confirmation: Confirmation) = confirmation.let { Unit }

    fun requestSignOut() = Unit

    fun confirmSignOut(confirmation: Confirmation) = confirmation.let { Unit }

    fun requestDeleteAccount() = Unit

    fun confirmDeleteAccount(confirmation: Confirmation) = confirmation.let { Unit }

    fun clearMessage() = Unit

    fun close() = Unit
}

class SyncStateHolder internal constructor() {
    val state: StateFlow<SyncUiState> =
        MutableStateFlow(SyncUiState(SyncStatus.Idle, true, null))

    fun requestSync(reason: SyncTrigger) = reason.let { Unit }

    fun retryFailed() = Unit

    fun clearMessage() = Unit

    fun close() = Unit
}
