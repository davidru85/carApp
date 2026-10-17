package com.ruizurraca.carapp

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import java.util.Locale

/** Marks a screen or section title as a heading, so TalkBack can move between headings (`E4-02`). */
internal fun Modifier.screenHeading(): Modifier = semantics { heading() }

/**
 * Announces an error as soon as it appears (`E4-02`). `Polite` lets the current utterance finish
 * instead of interrupting it, which is the TalkBack convention for validation and failure messages.
 */
internal fun Modifier.announcedError(): Modifier = semantics { liveRegion = LiveRegionMode.Polite }

/** A progress indicator that TalkBack names, instead of an unlabeled spinner (`E4-02`). */
@Composable
internal fun LabelledProgressIndicator(
    modifier: Modifier = Modifier,
    labelResource: Int = R.string.loading,
) {
    val label = stringResource(labelResource)
    CircularProgressIndicator(modifier = modifier.semantics { contentDescription = label })
}

/**
 * The locale that renders this composition (`E4-02`, `D-222`). It follows the configuration rather
 * than `Locale.getDefault()`, so a per-app language and a test's `DeviceConfigurationOverride.Locales`
 * format numbers and dates the same way they resolve strings.
 */
@Composable
@ReadOnlyComposable
internal fun currentDisplayLocale(): Locale {
    val locales = LocalConfiguration.current.locales
    return if (locales.size() == 0) Locale.getDefault() else locales.get(0)
}
