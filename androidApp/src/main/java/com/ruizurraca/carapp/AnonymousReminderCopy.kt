package com.ruizurraca.carapp

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * Maps a `D-62` reminder index to the notice body shown on Android. Every body states the benefit
 * of permanent sign-in and the device-bound 30-day cleanup risk of `docs/CONTRACTS.md §11.2`; the
 * later ones state how little time is left. Android offers Google only (`SPECIFICATION.md §7 F-1`),
 * so the copy names that provider and no other.
 */
internal fun anonymousReminderBodyResource(index: Int): Int =
    when {
        index <= 0 -> R.string.anonymous_reminder_body_1
        index == 1 -> R.string.anonymous_reminder_body_2
        index == 2 -> R.string.anonymous_reminder_body_3
        else -> R.string.anonymous_reminder_body_4
    }

/**
 * The foreground retention notice. It is dismissible, it renders above the authenticated content
 * without covering it, and it offers no action of its own, so it can never gate a feature.
 */
@Composable
internal fun AnonymousReminderBanner(
    index: Int,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier.fillMaxWidth().testTag(AnonymousReminderTestTags.BANNER),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.anonymous_reminder_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(anonymousReminderBodyResource(index)),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End).testTag(AnonymousReminderTestTags.DISMISS),
            ) {
                Text(stringResource(R.string.anonymous_reminder_dismiss))
            }
        }
    }
}

/**
 * Runs [onForeground] on every entry into the foreground, including the first one after launch, and
 * supplies how long the app spent in the background (`null` on the cold start).
 *
 * This is the whole trigger of `docs/CONTRACTS.md §11.3`: no scheduler, alarm or operating-system
 * notification is involved. The same lifecycle edge is also the `§9.8` `AppForeground` trigger, so the
 * duration is measured once here and passed to its consumer rather than observed twice.
 *
 * `ON_STOP` is the correct background edge because the app is a single-Activity host: the Activity
 * stops exactly when the app stops being visible. The clock is monotonic, so a wall-clock change while
 * the app is backgrounded cannot distort the duration.
 *
 * The measurement itself is process-scoped ([AndroidForegroundTracking]) and deliberately not held by
 * this composition: `LifecycleRegistry` re-dispatches `ON_START` to an observer added while the
 * lifecycle is already `STARTED`, so a tracker recreated with the Activity would report `null` - the
 * cold-start value - on a return that is neither a cold start nor a stay past the `§9.8` threshold.
 */
@Composable
internal fun OnForegroundReturn(onForeground: (backgroundMillis: Long?) -> Unit) {
    // Process-scoped, not created by this composition: an Activity recreation is not a cold
    // start (`§9.8`).
    val duration = AndroidForegroundTracking.duration
    val activity = LocalContext.current as? ComponentActivity
    DisposableEffect(activity, onForeground) {
        val lifecycle = activity?.lifecycle
        if (lifecycle == null) {
            onDispose { }
        } else {
            val observer =
                LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_STOP -> duration.onBackgrounded()
                        Lifecycle.Event.ON_START -> onForeground(duration.onForegrounded())
                        else -> Unit
                    }
                }
            lifecycle.addObserver(observer)
            onDispose { lifecycle.removeObserver(observer) }
        }
    }
}

object AnonymousReminderTestTags {
    const val BANNER = "anonymous_reminder_banner"
    const val DISMISS = "anonymous_reminder_dismiss"
}
