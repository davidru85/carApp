package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.DispatcherProvider
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.STATE_HOLDER_TIMEOUT_MS
import com.ruizurraca.carapp.core.common.SUPPORTED_CURRENCY_CODES
import com.ruizurraca.carapp.core.common.UiMessage
import com.ruizurraca.carapp.core.common.UiMessageKind
import com.ruizurraca.carapp.core.common.ValidationError
import com.ruizurraca.carapp.core.model.CurrencyCode
import com.ruizurraca.carapp.core.model.DistanceUnit
import com.ruizurraca.carapp.core.model.UserSettings
import com.ruizurraca.carapp.core.model.VolumeUnit
import com.ruizurraca.carapp.feature.session.domain.SettingsRepository
import com.ruizurraca.carapp.feature.session.domain.UpdateSettingsCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext

/**
 * The Settings surface of `docs/SPECIFICATION.md §3.1`: the currency, the read-only units and the
 * analytics opt-in (`docs/CONTRACTS.md §20.10`).
 *
 * It observes the graph's single settings flow, the one the `§16.1` opt-in gate also observes, so an
 * opt-in or an opt-out written here reaches the tracker without a restart. Backup status and the
 * session rows of the Settings screen stay on `SyncStateHolder` and `SessionStateHolder`.
 */
class SettingsStateHolder internal constructor(
    scope: CoroutineScope,
    private val repository: SettingsRepository,
    private val dispatchers: DispatcherProvider,
) {
    private val holderJob = SupervisorJob(scope.coroutineContext[Job])
    private val holderScope = CoroutineScope(scope.coroutineContext + holderJob)
    private val saving = MutableStateFlow(false)
    private val transientMessage = MutableStateFlow<UiMessage?>(null)
    private var closed = false

    // The last snapshot folded from the repository. It outlives a `WhileSubscribed` restart, so a renewed
    // observation starts from the settings already published instead of republishing them as unknown
    // (`docs/CONTRACTS.md §20.10`). Only the single `stateIn` upstream collector touches it, on `main`.
    private var lastSnapshot = EMPTY_SNAPSHOT

    val state: StateFlow<SettingsUiState> =
        combine(
            settingsSnapshots(),
            saving,
            transientMessage,
        ) { snapshot, isSaving, message ->
            snapshot.toUiState(
                isSaving = isSaving,
                message = message ?: snapshot.readError?.toSettingsMessage(SETTINGS_READ_ERROR_MESSAGE_ID),
            )
        }.stateIn(
            scope = holderScope + dispatchers.main,
            started = SharingStarted.WhileSubscribed(STATE_HOLDER_TIMEOUT_MS),
            initialValue = EMPTY_SNAPSHOT.toUiState(isSaving = false, message = null),
        )

    fun selectCurrency(code: String) {
        if (closed || saving.value) return
        // The published value is the comparison base, so re-selecting what the host shows is a no-op.
        if (code == state.value.currencyCode) return
        if (code !in SUPPORTED_CURRENCY_CODES) {
            transientMessage.value =
                ValidationError.InvalidUnit(code).toSettingsMessage(SETTINGS_WRITE_ERROR_MESSAGE_ID)
            return
        }
        write(UpdateSettingsCommand(currency = CurrencyCode(code), analyticsEnabled = null))
    }

    fun setAnalyticsEnabled(enabled: Boolean) {
        if (closed || saving.value) return
        val current = state.value
        // Before the first emission the published flag is a placeholder, not a value to compare with.
        if (current.currencyCode != null && current.analyticsEnabled == enabled) return
        write(UpdateSettingsCommand(currency = null, analyticsEnabled = enabled))
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

    // `docs/CONTRACTS.md §14`: the database flow is collected on `io`; the fold that maps each
    // `Outcome` runs downstream of `flowOn`, in the `stateIn` coroutine on `main`. It emits no seed: before
    // the first repository answer the published state is the `stateIn` initial value.
    private fun settingsSnapshots(): Flow<SettingsSnapshot> =
        repository.settings
            .flowOn(dispatchers.io)
            .map { result -> lastSnapshot.next(result).also { snapshot -> lastSnapshot = snapshot } }

    private fun write(command: UpdateSettingsCommand) {
        saving.value = true
        transientMessage.value = null
        holderScope.launch(dispatchers.main) {
            val result = withContext(dispatchers.io) { repository.updateSettings(command) }
            if (result is Outcome.Err) {
                transientMessage.value = result.error.toSettingsMessage(SETTINGS_WRITE_ERROR_MESSAGE_ID)
            }
            saving.value = false
        }
    }
}

/**
 * The last settings the repository emitted and the read failure that followed them, if any. A
 * failure keeps the settings it followed, so a transient read error does not blank the screen.
 */
private data class SettingsSnapshot(
    val settings: UserSettings?,
    val readError: AppError?,
) {
    fun next(result: Outcome<UserSettings, AppError>): SettingsSnapshot =
        when (result) {
            is Outcome.Ok -> SettingsSnapshot(settings = result.value, readError = null)
            is Outcome.Err -> copy(readError = result.error)
        }

    fun toUiState(
        isSaving: Boolean,
        message: UiMessage?,
    ): SettingsUiState =
        SettingsUiState(
            currencyCode = settings?.currency?.value,
            supportedCurrencyCodes = SUPPORTED_CURRENCY_CODE_LIST,
            // The MVP units are fixed and read-only (`docs/CONTRACTS.md §3`), so before the first
            // emission the placeholders are those fixed values.
            distanceUnit = settings?.distanceUnit ?: DistanceUnit.KM,
            volumeUnit = settings?.volumeUnit ?: VolumeUnit.LITER,
            analyticsEnabled = settings?.analyticsEnabled ?: false,
            isSaving = isSaving,
            message = message,
        )
}

private fun AppError.toSettingsMessage(id: Long): UiMessage =
    UiMessage(
        id = id,
        kind = UiMessageKind.ERROR,
        code = code,
        confirmation = null,
    )

private val EMPTY_SNAPSHOT = SettingsSnapshot(settings = null, readError = null)

/** `SUPPORTED_CURRENCY_CODES` in its declared order (`docs/CONTRACTS.md §20.0.1`). */
private val SUPPORTED_CURRENCY_CODE_LIST: List<String> = SUPPORTED_CURRENCY_CODES.toList()

// The `:shared` message ids continue the sequence of `StateHolders.kt`, which ends at 7.
private const val SETTINGS_READ_ERROR_MESSAGE_ID = 8L
private const val SETTINGS_WRITE_ERROR_MESSAGE_ID = 9L
