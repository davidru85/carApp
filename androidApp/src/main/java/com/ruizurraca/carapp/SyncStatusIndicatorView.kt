package com.ruizurraca.carapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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

/**
 * The discreet backup status indicator of `docs/SPECIFICATION.md §3.1`, drawn as the status chip both
 * platform designs already place on this screen (`docs/DESIGN.md §4`, screen 02 home).
 *
 * It renders the value the shared layer publishes and nothing else: the precedence and the
 * connectivity rule belong to `docs/CONTRACTS.md §9.9`, and `§14` forbids this screen from computing a
 * second `SyncStatus`. The retry affordance appears only for the visual `§9.9` defines as an error,
 * and it calls the existing `SyncStateHolder.retryFailed()`, so a failure it produces surfaces through
 * the holder's typed `UiMessage` and needs no channel of its own.
 *
 * The dot is decorative and carries no semantics; the row is announced as the status sentence, which
 * is why the row clears its children's semantics rather than exposing a bare coloured box.
 */
@Composable
internal fun SyncStatusIndicator(
    status: SyncStatus,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visual = syncStatusVisual(status)
    val isError = syncStatusIsError(visual)
    val label = stringResource(syncStatusLabelResource(visual))
    val description = stringResource(R.string.backup_status_description, label)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .testTag(SyncStatusTestTags.INDICATOR),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .background(
                        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        shape = CircleShape,
                    )
                    // The coloured dot carries the state for a sighted reader. It is announced as the
                    // status sentence so a screen reader hears the state rather than a bare box;
                    // `E4-02` audits the full accessibility surface. The description is set here
                    // rather than on the row, because clearing the row's semantics would also hide the
                    // label and this indicator's own test tag.
                    .semantics { contentDescription = description },
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
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
}

object SyncStatusTestTags {
    const val INDICATOR = "backup_status_indicator"
    const val RETRY = "backup_status_retry"
}
