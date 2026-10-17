package com.ruizurraca.carapp

import androidx.test.platform.app.InstrumentationRegistry
import com.ruizurraca.carapp.core.common.AuthProvider
import com.ruizurraca.carapp.core.common.Confirmation
import com.ruizurraca.carapp.core.common.UiMessage
import com.ruizurraca.carapp.core.common.UiMessageKind

/** A published session in [phase], with the provider list `SessionStateHolder` derives for it. */
internal fun conversionSession(
    phase: SessionPhase,
    isBusy: Boolean = false,
    message: UiMessage? = null,
): SessionUiState =
    SessionUiState(
        phase = phase,
        providers =
            when (phase) {
                SessionPhase.ANONYMOUS -> listOf(AuthProvider.ANONYMOUS)
                SessionPhase.PERMANENT -> listOf(AuthProvider.GOOGLE)
                else -> emptyList()
            },
        isBusy = isBusy,
        message = message,
        anonymousReminderIndex = null,
        pendingSyncCount = null,
        pendingDepartureRetry = null,
    )

/** The exact collision confirmation `SessionStateHolder` publishes (`StateHolders.kt:651-657`). */
internal fun collisionConfirmation(): UiMessage =
    UiMessage(
        id = 3L,
        kind = UiMessageKind.WARNING,
        code = "CONFIRMATION.AdoptExistingAccount",
        confirmation = Confirmation.AdoptExistingAccount,
    )

internal fun conversionString(resource: Int): String =
    InstrumentationRegistry.getInstrumentation().targetContext.getString(resource)
