package com.ruizurraca.carapp

import androidx.compose.runtime.Composable
import androidx.test.platform.app.InstrumentationRegistry
import com.ruizurraca.carapp.core.common.Confirmation
import com.ruizurraca.carapp.core.common.SyncStatus
import com.ruizurraca.carapp.core.common.UiMessage
import com.ruizurraca.carapp.core.common.UiMessageKind
import com.ruizurraca.carapp.core.model.DistanceUnit
import com.ruizurraca.carapp.core.model.VolumeUnit

/** A version string no build ships, so a test that passes it proves the screen shows its input. */
internal const val TEST_APP_VERSION = "9.9.9-test"

internal fun settingsState(
    currencyCode: String? = "EUR",
    analyticsEnabled: Boolean = false,
    isSaving: Boolean = false,
    message: UiMessage? = null,
): SettingsUiState =
    SettingsUiState(
        currencyCode = currencyCode,
        supportedCurrencyCodes = listOf("EUR", "GBP", "USD"),
        distanceUnit = DistanceUnit.KM,
        volumeUnit = VolumeUnit.LITER,
        analyticsEnabled = analyticsEnabled,
        isSaving = isSaving,
        message = message,
    )

internal fun sessionState(
    phase: SessionPhase,
    isBusy: Boolean = false,
    message: UiMessage? = null,
    pendingSyncCount: Int? = null,
    pendingDepartureRetry: DepartureRetry? = null,
): SessionUiState =
    SessionUiState(
        phase = phase,
        providers = emptyList(),
        isBusy = isBusy,
        message = message,
        anonymousReminderIndex = null,
        pendingSyncCount = pendingSyncCount,
        pendingDepartureRetry = pendingDepartureRetry,
    )

internal fun syncState(status: SyncStatus = SyncStatus.Idle): SyncUiState =
    SyncUiState(status = status, isOnline = true, message = null)

internal fun uiMessage(
    code: String,
    kind: UiMessageKind,
    confirmation: Confirmation?,
): UiMessage = UiMessage(id = 1L, kind = kind, code = code, confirmation = confirmation)

internal fun recordingSettingsCallbacks(calls: MutableList<String>): SettingsCallbacks =
    SettingsCallbacks(
        onBack = { calls += "back" },
        onSelectCurrency = { code -> calls += "currency:$code" },
        onAnalyticsEnabledChange = { enabled -> calls += "analytics:$enabled" },
        onRetryBackup = { calls += "retryBackup" },
        onSignOut = { calls += "signOut" },
        onDeleteAccount = { calls += "deleteAccount" },
        onDeleteLocalData = { calls += "deleteLocalData" },
        onDismissSettingsMessage = { calls += "dismissSettingsMessage" },
        onLinkAccount = { provider -> calls += "link:$provider" },
    )

internal fun recordingSessionPromptCallbacks(calls: MutableList<String>): SessionPromptCallbacks =
    SessionPromptCallbacks(
        onWaitForBackup = { calls += "wait" },
        onWithdraw = { calls += "withdraw" },
        onDiscardPendingChanges = { calls += "discard" },
        onConfirmDeleteAccount = { calls += "confirmDeleteAccount" },
        onConfirmDeleteLocalData = { calls += "confirmDeleteLocalData" },
        onDismissError = { calls += "dismissError" },
        onConfirmAccountConversion = { calls += "confirmAccountConversion" },
        onCancelAccountConversion = { calls += "cancelAccountConversion" },
    )

@Composable
internal fun TestSettingsScreen(
    settings: SettingsUiState = settingsState(),
    session: SessionUiState = sessionState(SessionPhase.ANONYMOUS),
    sync: SyncUiState = syncState(),
    appVersion: String = TEST_APP_VERSION,
    callbacks: SettingsCallbacks = recordingSettingsCallbacks(mutableListOf()),
) {
    SettingsScreen(
        settings = settings,
        session = session,
        sync = sync,
        appVersion = appVersion,
        callbacks = callbacks,
    )
}

internal fun targetString(
    resource: Int,
    vararg arguments: Any,
): String = InstrumentationRegistry.getInstrumentation().targetContext.getString(resource, *arguments)

internal fun targetQuantityString(
    resource: Int,
    count: Int,
): String =
    InstrumentationRegistry
        .getInstrumentation()
        .targetContext.resources
        .getQuantityString(resource, count, count)
