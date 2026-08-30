package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.auth.AuthClient
import com.ruizurraca.carapp.core.auth.AuthSession
import com.ruizurraca.carapp.core.auth.AuthState
import com.ruizurraca.carapp.core.common.AuthProvider
import com.ruizurraca.carapp.core.common.Confirmation
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.SyncStatus
import com.ruizurraca.carapp.core.common.SyncTrigger
import com.ruizurraca.carapp.core.common.UiMessage
import com.ruizurraca.carapp.core.common.UiMessageKind
import com.ruizurraca.carapp.core.model.FuelType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

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

class SessionStateHolder internal constructor(
    private val scope: CoroutineScope? = null,
    private val authClient: AuthClient? = null,
) {
    private var closed = false
    private var operationJob: Job? = null
    private val mutableState =
        MutableStateFlow(authClient?.authState?.value.toSessionUiState())
    val state: StateFlow<SessionUiState> = mutableState

    fun startAnonymousSignIn() {
        if (closed) return
        val operationScope = scope ?: return
        val client = authClient ?: return
        mutableState.value = mutableState.value.copy(isBusy = true, message = null)
        operationJob =
            operationScope.launch {
                mutableState.value =
                    when (val result = client.signInAnonymously()) {
                        is Outcome.Ok -> {
                            result.value.toSessionUiState()
                        }

                        is Outcome.Err -> {
                            SessionUiState(
                                phase = SessionPhase.LOCAL,
                                providers = emptyList(),
                                isBusy = false,
                                message =
                                    UiMessage(
                                        id = LOCAL_AUTH_MESSAGE_ID,
                                        kind = UiMessageKind.WARNING,
                                        code = result.error.code,
                                        confirmation = null,
                                    ),
                            )
                        }
                    }
            }
    }

    fun startPermanentSignIn(provider: AuthProvider) = provider.let { Unit }

    fun startAccountConversion(provider: AuthProvider) = provider.let { Unit }

    fun confirmAccountConversion(confirmation: Confirmation) = confirmation.let { Unit }

    fun requestSignOut() = Unit

    fun confirmSignOut(confirmation: Confirmation) = confirmation.let { Unit }

    fun requestDeleteAccount() = Unit

    fun confirmDeleteAccount(confirmation: Confirmation) = confirmation.let { Unit }

    fun clearMessage() = Unit

    fun close() {
        if (closed) return
        closed = true
        operationJob?.cancel()
        operationJob = null
    }
}

private fun AuthState?.toSessionUiState(): SessionUiState =
    when (this) {
        null,
        AuthState.Unknown,
        -> SessionUiState(SessionPhase.UNKNOWN, emptyList(), false, null)

        AuthState.SignedOut -> SessionUiState(SessionPhase.SIGNED_OUT, emptyList(), false, null)

        is AuthState.SignedIn -> session.toSessionUiState()
    }

private fun AuthSession.toSessionUiState(): SessionUiState =
    SessionUiState(
        phase = if (isAnonymous) SessionPhase.ANONYMOUS else SessionPhase.PERMANENT,
        providers = AuthProvider.entries.filter(providers::contains),
        isBusy = false,
        message = null,
    )

private const val LOCAL_AUTH_MESSAGE_ID = 1L

class SyncStateHolder internal constructor() {
    val state: StateFlow<SyncUiState> =
        MutableStateFlow(SyncUiState(SyncStatus.Idle, true, null))

    fun requestSync(reason: SyncTrigger) = reason.let { Unit }

    fun retryFailed() = Unit

    fun clearMessage() = Unit

    fun close() = Unit
}
