package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.common.DispatcherProvider
import com.ruizurraca.carapp.core.model.DistanceUnit
import com.ruizurraca.carapp.core.model.VolumeUnit
import com.ruizurraca.carapp.feature.session.domain.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The Settings surface of `docs/SPECIFICATION.md §3.1`: the currency, the read-only units and the
 * analytics opt-in (`docs/CONTRACTS.md §20.10`).
 *
 * It observes the graph's single settings flow, the one the `§16.1` opt-in gate also observes, so an
 * opt-in or an opt-out written here reaches the tracker without a restart. Backup status and the
 * session rows of the Settings screen stay on `SyncStateHolder` and `SessionStateHolder`.
 */
@Suppress("UnusedPrivateProperty", "UnusedParameter", "UnusedPrivateMember")
class SettingsStateHolder internal constructor(
    scope: CoroutineScope,
    private val repository: SettingsRepository,
    private val dispatchers: DispatcherProvider,
) {
    // RED (E4-01): a fixed placeholder state and no-op intents, so the specification compiles,
    // executes and fails for the behaviour it expresses. The suppression above exists only for this.
    val state: StateFlow<SettingsUiState> = MutableStateFlow(RED_STUB_STATE)

    fun selectCurrency(code: String) = Unit

    fun setAnalyticsEnabled(enabled: Boolean) = Unit

    fun clearMessage() = Unit

    fun close() = Unit
}

private val RED_STUB_STATE =
    SettingsUiState(
        currencyCode = "",
        supportedCurrencyCodes = emptyList(),
        distanceUnit = DistanceUnit.MILES,
        volumeUnit = VolumeUnit.GALLON,
        analyticsEnabled = false,
        isSaving = false,
        message = null,
    )
