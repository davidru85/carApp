package com.ruizurraca.carapp

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.ruizurraca.carapp.core.common.AuthError
import com.ruizurraca.carapp.core.common.Confirmation
import com.ruizurraca.carapp.core.common.SyncTrigger
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

/**
 * Every answer the departure prompts can give. Each lambda is one existing shared intent, or the
 * composition of intents the contract names (`docs/CONTRACTS.md §20.10`); see [sessionPromptCallbacks].
 */
internal data class SessionPromptCallbacks(
    val onWaitForBackup: () -> Unit,
    val onWithdraw: () -> Unit,
    val onDiscardPendingChanges: () -> Unit,
    val onConfirmDeleteAccount: () -> Unit,
    val onConfirmDeleteLocalData: () -> Unit,
    val onDismissError: () -> Unit,
)

/**
 * Binds the prompts to the shared holders.
 *
 * - "Wait" withdraws the warning and asks for a user-initiated cycle (B1-D3). The owner repeats the
 *   request later, and the warning reappears while rows remain.
 * - "Discard" answers the request the warning belongs to, which the phase names ([pendingSyncDiscard]):
 *   `confirmSignOut` for a permanent owner, `confirmDeleteAccount` for a local or anonymous one.
 * - Re-authentication is not answered here: the root routes it to the departure surface (B1-D4).
 */
internal fun sessionPromptCallbacks(
    session: SessionStateHolder,
    sync: SyncStateHolder,
    phase: SessionPhase,
): SessionPromptCallbacks =
    SessionPromptCallbacks(
        onWaitForBackup = {
            session.clearMessage()
            sync.requestSync(SyncTrigger.PullToRefresh)
        },
        onWithdraw = session::clearMessage,
        onDiscardPendingChanges = {
            when (pendingSyncDiscard(phase)) {
                PendingSyncDiscard.SIGN_OUT -> {
                    session.confirmSignOut(Confirmation.DiscardPendingChanges)
                }

                PendingSyncDiscard.LOCAL_DATA_DELETION -> {
                    session.confirmDeleteAccount(Confirmation.DiscardPendingChanges)
                }
            }
        },
        onConfirmDeleteAccount = { session.confirmDeleteAccount(Confirmation.DeleteAccount) },
        onConfirmDeleteLocalData = { session.confirmDeleteAccount(Confirmation.DeleteLocalData) },
        onDismissError = session::clearMessage,
    )

/**
 * The departure prompts of the Android host. They are drawn at the root of the authenticated branch
 * (`VehicleApp` in `MainActivity.kt`), not by the Settings route: a failed deletion returns the phase
 * to `PERMANENT`, which re-mounts the `NavHost` at the vehicle list (B1-D4). Only `ERROR` messages are
 * raised as failures ([sessionPrompt]); a stale login is the departure surface's, not a dialog here.
 */
@Composable
internal fun SettingsSessionPrompts(
    session: SessionUiState,
    callbacks: SessionPromptCallbacks,
) {
    val message = session.message ?: return
    when (sessionPrompt(message)) {
        SessionPrompt.NONE -> {
            Unit
        }

        SessionPrompt.PENDING_SYNC -> {
            PendingSyncDialog(pendingSyncCount = session.pendingSyncCount, callbacks = callbacks)
        }

        SessionPrompt.CONFIRM_DELETE_ACCOUNT -> {
            DestructiveConfirmationDialog(
                dialogTag = SettingsTestTags.CONFIRM_DELETE_ACCOUNT_DIALOG,
                titleResource = R.string.confirm_delete_account_title,
                message = message,
                confirmLabelResource = R.string.settings_delete_account,
                onConfirm = callbacks.onConfirmDeleteAccount,
                onWithdraw = callbacks.onWithdraw,
            )
        }

        SessionPrompt.CONFIRM_DELETE_LOCAL_DATA -> {
            DestructiveConfirmationDialog(
                dialogTag = SettingsTestTags.CONFIRM_DELETE_LOCAL_DATA_DIALOG,
                titleResource = R.string.confirm_delete_local_data_title,
                message = message,
                confirmLabelResource = R.string.settings_delete_local_data,
                onConfirm = callbacks.onConfirmDeleteLocalData,
                onWithdraw = callbacks.onWithdraw,
            )
        }

        // The root routes a stale login to `DepartureScreen`, which carries re-authentication.
        SessionPrompt.REAUTHENTICATE -> {
            Unit
        }

        SessionPrompt.ERROR -> {
            SessionErrorDialog(message = message, onDismiss = callbacks.onDismissError)
        }
    }
}

/** F-5: the exact count of rows not yet backed up, and the wait, cancel and discard answers. */
@Composable
private fun PendingSyncDialog(
    pendingSyncCount: Int?,
    callbacks: SessionPromptCallbacks,
) {
    AlertDialog(
        onDismissRequest = callbacks.onWithdraw,
        modifier = Modifier.testTag(SettingsTestTags.PENDING_SYNC_DIALOG),
        title = { Text(stringResource(R.string.pending_sync_title)) },
        text = {
            pendingSyncCount?.let { count ->
                Text(
                    text = pluralStringResource(R.plurals.pending_sync_body, count, count),
                    modifier = Modifier.testTag(SettingsTestTags.PENDING_SYNC_BODY),
                )
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(
                    onClick = callbacks.onWaitForBackup,
                    modifier = Modifier.testTag(SettingsTestTags.PENDING_SYNC_WAIT),
                ) {
                    Text(stringResource(R.string.pending_sync_wait))
                }
                TextButton(
                    onClick = callbacks.onDiscardPendingChanges,
                    modifier = Modifier.testTag(SettingsTestTags.PENDING_SYNC_DISCARD),
                ) {
                    Text(
                        text = stringResource(R.string.pending_sync_discard),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = callbacks.onWithdraw,
                modifier = Modifier.testTag(SettingsTestTags.PENDING_SYNC_CANCEL),
            ) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

/** The second step of the two-step destructive protocol; its body is the confirmation's own copy. */
@Composable
private fun DestructiveConfirmationDialog(
    dialogTag: String,
    titleResource: Int,
    message: UiMessage,
    confirmLabelResource: Int,
    onConfirm: () -> Unit,
    onWithdraw: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onWithdraw,
        modifier = Modifier.testTag(dialogTag),
        title = { Text(stringResource(titleResource)) },
        text = { Text(stringResource(message.stringResource())) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag(SettingsTestTags.CONFIRM_DESTRUCTIVE),
            ) {
                Text(
                    text = stringResource(confirmLabelResource),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onWithdraw,
                modifier = Modifier.testTag(SettingsTestTags.CANCEL_DESTRUCTIVE),
            ) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

/** A typed departure failure that retains nothing to retry, localized through the shared mapping. */
@Composable
private fun SessionErrorDialog(
    message: UiMessage,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(SettingsTestTags.SESSION_ERROR_DIALOG),
        text = {
            Text(
                text = stringResource(sessionMessageStringResource(message)),
                modifier = Modifier.testTag(SettingsTestTags.SESSION_ERROR_TEXT),
            )
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag(SettingsTestTags.SESSION_ERROR_DISMISS),
            ) {
                Text(stringResource(R.string.ok))
            }
        },
    )
}
