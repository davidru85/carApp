package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.common.AuthError
import com.ruizurraca.carapp.core.common.Confirmation
import com.ruizurraca.carapp.core.common.UiMessage
import com.ruizurraca.carapp.core.common.UiMessageKind

/** The prompt a published `SessionUiState.message` asks the Android host to show. */
internal enum class SessionPrompt {
    NONE,
    PENDING_SYNC,
    CONFIRM_DELETE_ACCOUNT,
    CONFIRM_DELETE_LOCAL_DATA,
    REAUTHENTICATE,
    ERROR,
}

/**
 * Classifies a published `SessionUiState.message` (`docs/CONTRACTS.md §20.10`).
 *
 * - The three departure confirmations of `§20.2` each have their own prompt.
 * - `AuthError.RequiresRecentLogin` is the `§20.10` re-authentication flow, never a plain error.
 * - Every other ERROR is a failure the owner must see.
 * - A WARNING without a confirmation is the F-1 local start of `startAnonymousSignIn`, not a departure.
 * - `AdoptExistingAccount` belongs to the `E4-05` conversion surface, and `OdometerInconsistent` is
 *   never published by the session holder.
 */
internal fun sessionPrompt(message: UiMessage?): SessionPrompt =
    when (message?.confirmation) {
        Confirmation.DiscardPendingChanges -> SessionPrompt.PENDING_SYNC

        Confirmation.DeleteAccount -> SessionPrompt.CONFIRM_DELETE_ACCOUNT

        Confirmation.DeleteLocalData -> SessionPrompt.CONFIRM_DELETE_LOCAL_DATA

        Confirmation.AdoptExistingAccount,
        Confirmation.OdometerInconsistent,
        -> SessionPrompt.NONE

        null -> unconfirmedSessionPrompt(message)
    }

private fun unconfirmedSessionPrompt(message: UiMessage?): SessionPrompt =
    when {
        message == null -> SessionPrompt.NONE
        message.code == AuthError.RequiresRecentLogin.code -> SessionPrompt.REAUTHENTICATE
        message.kind == UiMessageKind.ERROR -> SessionPrompt.ERROR
        else -> SessionPrompt.NONE
    }

/** Which confirmation intent answers a pending-sync warning (`docs/CONTRACTS.md §20.10`). */
internal enum class PendingSyncDiscard { SIGN_OUT, LOCAL_DATA_DELETION }

/**
 * A pending-sync warning answers either a permanent owner's sign-out or a local or anonymous owner's
 * local-data deletion; no other request publishes it (`docs/CONTRACTS.md §20.10`). The phase therefore
 * names the request, and with it the intent that accepts the discard.
 */
internal fun pendingSyncDiscard(phase: SessionPhase): PendingSyncDiscard =
    if (phase == SessionPhase.PERMANENT) PendingSyncDiscard.SIGN_OUT else PendingSyncDiscard.LOCAL_DATA_DELETION
