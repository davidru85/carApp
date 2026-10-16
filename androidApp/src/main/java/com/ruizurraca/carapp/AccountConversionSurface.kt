package com.ruizurraca.carapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ruizurraca.carapp.core.common.AuthProvider
import com.ruizurraca.carapp.core.common.Confirmation
import com.ruizurraca.carapp.core.common.UiMessage

/**
 * `E4-05`: the F-4 entry point is offered only to an anonymous session. `startAccountConversion`
 * refuses every other session (`docs/CONTRACTS.md §20.10`), and `D-224` keeps the row hidden
 * while the session is still `LOCAL`, until adoption makes it anonymous.
 */
internal fun showsLinkAccountRow(phase: SessionPhase): Boolean =
    when (phase) {
        SessionPhase.ANONYMOUS -> {
            true
        }

        SessionPhase.UNKNOWN,
        SessionPhase.LOCAL,
        SessionPhase.PERMANENT,
        SessionPhase.SIGNED_OUT,
        SessionPhase.DELETING,
        -> {
            false
        }
    }

/** The one session message the conversion surface answers itself (`docs/CONTRACTS.md §11.3`). */
internal fun isAccountConversionConfirmation(message: UiMessage?): Boolean =
    message?.confirmation == Confirmation.AdoptExistingAccount

/**
 * Copy for a message of the session channel. On that channel a `REMOTE.*` code is published only by
 * a confirmed account replacement that failed after the session switch, which the app resumes on its
 * own (`docs/CONTRACTS.md §11.3`); departures publish only `AUTH.*` and `PERSISTENCE.*` codes. Every
 * other code keeps the shared mapping.
 */
internal fun sessionMessageStringResource(message: UiMessage): Int =
    if (message.code.startsWith(REMOTE_CODE_PREFIX)) {
        R.string.error_conversion_replacement_pending
    } else {
        message.stringResource()
    }

/**
 * The anonymous-only "link account" row. Android offers Google only (`docs/SPECIFICATION.md §7 F-1`).
 * It holds no conversion logic: the caller routes the provider to the native picker, which starts with
 * `SessionStateHolder.startAccountConversion` (`E4-05`). Every tap is refused while the session is busy,
 * so a second attempt cannot start while one is running.
 */
@Composable
internal fun LinkAccountSection(
    session: SessionUiState,
    onLinkAccount: (AuthProvider) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!showsLinkAccountRow(session.phase)) return
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .testTag(SettingsTestTags.LINK_ACCOUNT_ROW),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_link_account_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.settings_link_account_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = { onLinkAccount(AuthProvider.GOOGLE) },
            enabled = !session.isBusy,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(SettingsTestTags.LINK_ACCOUNT_GOOGLE),
        ) {
            Text(stringResource(R.string.continue_with_google))
        }
        if (session.isBusy) {
            val progressDescription = stringResource(R.string.settings_link_account_in_progress)
            CircularProgressIndicator(
                modifier =
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .testTag(SettingsTestTags.LINK_ACCOUNT_PROGRESS)
                        .semantics { contentDescription = progressDescription },
            )
        }
    }
}

/**
 * The destructive credential-collision confirmation of `docs/CONTRACTS.md §11.3`: it states that the
 * existing permanent-account data will be replaced by this device's data. Dismissing it is cancelling
 * it, because nothing may be replaced without the explicit confirmation.
 */
@Composable
internal fun AccountConversionCollisionDialog(
    message: UiMessage?,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    if (message == null || !isAccountConversionConfirmation(message)) return
    AlertDialog(
        onDismissRequest = onCancel,
        modifier = Modifier.testTag(SettingsTestTags.CONVERSION_DIALOG),
        title = { Text(stringResource(R.string.conversion_collision_title)) },
        text = { Text(stringResource(message.stringResource())) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag(SettingsTestTags.CONVERSION_CONFIRM),
            ) {
                Text(
                    text = stringResource(R.string.conversion_collision_confirm),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.testTag(SettingsTestTags.CONVERSION_CANCEL),
            ) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

/**
 * A non-confirmation message of the session channel, rendered with its session-channel copy
 * ([sessionMessageStringResource]). Confirmations are dialogs and render nothing here.
 */
@Composable
internal fun SessionErrorText(
    message: UiMessage?,
    testTag: String,
) {
    if (message == null || message.confirmation != null) return
    Text(
        text = stringResource(sessionMessageStringResource(message)),
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.testTag(testTag),
    )
}

private const val REMOTE_CODE_PREFIX = "REMOTE."
