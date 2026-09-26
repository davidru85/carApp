package com.ruizurraca.carapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ruizurraca.carapp.core.common.SyncStatus
import com.ruizurraca.carapp.core.common.UiMessage

/**
 * Renders the shared backup status without deriving synchronization state in the host.
 * The visible label owns the localized accessibility description, the colored dot is decorative,
 * Failed is the only state with Retry, and a typed retry error is rendered through [ErrorText] only
 * beside Failed, because `§9.9` reserves the error presentation for it.
 */
@Composable
internal fun SyncStatusIndicator(
    status: SyncStatus,
    message: UiMessage?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visual = syncStatusVisual(status)
    val isError = syncStatusIsError(visual)
    val label = stringResource(syncStatusLabelResource(visual))
    val description = stringResource(R.string.backup_status_description, label)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(SyncStatusTestTags.INDICATOR),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(8.dp)
                        .background(
                            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                        ),
            )
            Text(
                text = label,
                modifier =
                    Modifier
                        .weight(1f)
                        .semantics { contentDescription = description },
                style = MaterialTheme.typography.labelMedium,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (isError) {
                TextButton(
                    onClick = onRetry,
                    modifier = Modifier.testTag(SyncStatusTestTags.RETRY),
                ) {
                    Text(stringResource(R.string.backup_status_retry))
                }
            }
        }
        // The holder withdraws the message when the status leaves Failed (`§14`); this guard also
        // covers the turn in which the vehicle-list relay has moved on and the sync holder has not.
        if (isError) {
            ErrorText(message, testTag = SyncStatusTestTags.ERROR)
        }
    }
}

object SyncStatusTestTags {
    const val INDICATOR = "backup_status_indicator"
    const val RETRY = "backup_status_retry"
    const val ERROR = "backup_status_error"
}
