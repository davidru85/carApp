package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.common.UiMessage

/** The prompt a published `SessionUiState.message` asks the Android host to show. */
internal enum class SessionPrompt {
    NONE,
    PENDING_SYNC,
    CONFIRM_DELETE_ACCOUNT,
    CONFIRM_DELETE_LOCAL_DATA,
    REAUTHENTICATE,
    ERROR,
}

/** RED stub: the `E4-01` GREEN commit replaces the body. */
@Suppress("UnusedParameter")
internal fun sessionPrompt(message: UiMessage?): SessionPrompt = SessionPrompt.NONE

/** Which confirmation intent answers a pending-sync warning (`docs/CONTRACTS.md §20.10`). */
internal enum class PendingSyncDiscard { SIGN_OUT, LOCAL_DATA_DELETION }

/** RED stub: the `E4-01` GREEN commit replaces the body. */
@Suppress("UnusedParameter")
internal fun pendingSyncDiscard(phase: SessionPhase): PendingSyncDiscard = PendingSyncDiscard.LOCAL_DATA_DELETION
