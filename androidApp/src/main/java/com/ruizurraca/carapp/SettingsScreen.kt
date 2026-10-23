package com.ruizurraca.carapp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ruizurraca.carapp.core.common.AuthProvider
import com.ruizurraca.carapp.core.common.UiMessage
import com.ruizurraca.carapp.core.common.UiMessageKind
import com.ruizurraca.carapp.core.model.DistanceUnit
import com.ruizurraca.carapp.core.model.VolumeUnit
import java.util.Currency
import java.util.Locale

/**
 * The session rows a phase offers (D-215). `UNKNOWN`, `SIGNED_OUT` and `DELETING` have no owner the
 * screen could act for, so they offer none; Settings is not reachable in those phases anyway.
 */
internal fun settingsSessionRows(phase: SessionPhase): List<SettingsSessionRow> =
    when (phase) {
        SessionPhase.PERMANENT -> listOf(SettingsSessionRow.SIGN_OUT, SettingsSessionRow.DELETE_ACCOUNT)

        SessionPhase.LOCAL,
        SessionPhase.ANONYMOUS,
        -> listOf(SettingsSessionRow.DELETE_LOCAL_DATA)

        SessionPhase.UNKNOWN,
        SessionPhase.SIGNED_OUT,
        SessionPhase.DELETING,
        -> emptyList()
    }

/** The label of each `DistanceUnit`. The `when` is exhaustive, so a new unit fails compilation here. */
internal fun distanceUnitLabelResource(unit: DistanceUnit): Int =
    when (unit) {
        DistanceUnit.KM -> R.string.unit_distance_km
        DistanceUnit.MILES -> R.string.unit_distance_miles
    }

/** The label of each `VolumeUnit`. The `when` is exhaustive, so a new unit fails compilation here. */
internal fun volumeUnitLabelResource(unit: VolumeUnit): Int =
    when (unit) {
        VolumeUnit.LITER -> R.string.unit_volume_liter
        VolumeUnit.GALLON -> R.string.unit_volume_gallon
    }

/**
 * The platform display name of an ISO 4217 code in [locale]. `SettingsUiState` carries only the code
 * (`docs/CONTRACTS.md §15.3`, D-211), so the host produces the name through the platform currency API.
 * A code the platform does not know is shown as itself instead of crashing the screen.
 */
internal fun currencyDisplayName(
    code: String,
    locale: Locale,
): String = runCatching { Currency.getInstance(code).getDisplayName(locale) }.getOrDefault(code)

/**
 * Every action the Settings screen can request. The screen is stateless: the route
 * (`SettingsRoute.kt`) binds each lambda to an existing shared intent, so this file owns no settings,
 * backup or departure behaviour (`AGENTS.md` §Product Rules).
 */
internal data class SettingsCallbacks(
    val onBack: () -> Unit,
    val onSelectCurrency: (String) -> Unit,
    val onAnalyticsEnabledChange: (Boolean) -> Unit,
    val onRetryBackup: () -> Unit,
    val onSignOut: () -> Unit,
    val onDeleteAccount: () -> Unit,
    val onDeleteLocalData: () -> Unit,
    val onDismissSettingsMessage: () -> Unit,
    /** `E4-05`: starts linking the anonymous session to [AuthProvider] through the native picker. */
    val onLinkAccount: (AuthProvider) -> Unit,
)

/**
 * The `E4-01` Settings screen: exactly the `docs/SPECIFICATION.md §3.1` surface, in its order —
 * currency, the two read-only units, backup status, the analytics opt-in, the D-215 session rows and
 * the app version. The anonymous-only link-account row is `E4-05`'s. Departure prompts are not drawn
 * here: `SettingsSessionPrompts` draws them at the root of the authenticated branch, because a failed
 * deletion re-mounts the `NavHost` at the vehicle list, and re-authentication lives on the departure
 * surface (D-214).
 */
@Composable
internal fun SettingsScreen(
    settings: SettingsUiState,
    session: SessionUiState,
    sync: SyncUiState,
    appVersion: String,
    callbacks: SettingsCallbacks,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.testTag(SettingsTestTags.SCREEN),
        topBar = { SettingsTopBar(onBack = callbacks.onBack) },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
        ) {
            SettingsMessage(message = settings.message, onDismiss = callbacks.onDismissSettingsMessage)
            UnitsAndCurrencySection(settings = settings, onSelectCurrency = callbacks.onSelectCurrency)
            BackupSection(sync = sync, onRetry = callbacks.onRetryBackup)
            PrivacySection(settings = settings, onAnalyticsEnabledChange = callbacks.onAnalyticsEnabledChange)
            AccountSection(session = session, callbacks = callbacks)
            HorizontalDivider()
            SettingsValueRow(
                labelResource = R.string.settings_version,
                value = appVersion,
                rowTag = SettingsTestTags.VERSION_ROW,
                valueTag = SettingsTestTags.VERSION_VALUE,
                onClick = null,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTopBar(onBack: () -> Unit) {
    TopAppBar(
        title = { Text(stringResource(R.string.settings_title), modifier = Modifier.screenHeading()) },
        navigationIcon = {
            TextButton(
                onClick = onBack,
                modifier = Modifier.testTag(SettingsTestTags.BACK),
            ) {
                Text(stringResource(R.string.back))
            }
        },
    )
}

@Composable
private fun SettingsSectionHeader(titleResource: Int) {
    Text(
        text = stringResource(titleResource),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier =
            Modifier
                .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp)
                .semantics { heading() },
    )
}

/**
 * A typed `SettingsUiState.message` of kind `ERROR`, localized through the one shared mapping, with a
 * dismiss action. Only errors are raised as failures, as on the departure prompts.
 */
@Composable
private fun SettingsMessage(
    message: UiMessage?,
    onDismiss: () -> Unit,
) {
    if (message == null || message.kind != UiMessageKind.ERROR) return
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            ErrorText(message, testTag = SettingsTestTags.ERROR)
        }
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.testTag(SettingsTestTags.DISMISS_ERROR),
        ) {
            Text(stringResource(R.string.ok))
        }
    }
}

/**
 * Currency is the only editable row (`§3.1`). Its picker opens once the first settings row is known
 * and while no write is in flight; the units are visible and offer no action at all.
 */
@Composable
private fun UnitsAndCurrencySection(
    settings: SettingsUiState,
    onSelectCurrency: (String) -> Unit,
) {
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]
    val currencyCode = settings.currencyCode
    // `currencyCode == null` is the readiness signal of `docs/CONTRACTS.md §20.10`; until the repository
    // has emitted the stored settings, the other fields are placeholders and MUST NOT be shown as known.
    val currencyValue =
        currencyCode?.let { code ->
            stringResource(R.string.settings_currency_value, currencyDisplayName(code, locale), code)
        } ?: stringResource(R.string.loading)
    SettingsSectionHeader(R.string.settings_section_units)
    SettingsValueRow(
        labelResource = R.string.settings_currency,
        value = currencyValue,
        rowTag = SettingsTestTags.CURRENCY_ROW,
        valueTag = SettingsTestTags.CURRENCY_VALUE,
        onClick = if (currencyCode != null && !settings.isSaving) ({ pickerOpen = true }) else null,
    )
    HorizontalDivider()
    SettingsValueRow(
        labelResource = R.string.settings_distance_unit,
        value =
            if (currencyCode == null) {
                stringResource(R.string.loading)
            } else {
                stringResource(distanceUnitLabelResource(settings.distanceUnit))
            },
        rowTag = SettingsTestTags.DISTANCE_UNIT_ROW,
        valueTag = null,
        onClick = null,
    )
    HorizontalDivider()
    SettingsValueRow(
        labelResource = R.string.settings_volume_unit,
        value =
            if (currencyCode == null) {
                stringResource(R.string.loading)
            } else {
                stringResource(volumeUnitLabelResource(settings.volumeUnit))
            },
        rowTag = SettingsTestTags.VOLUME_UNIT_ROW,
        valueTag = null,
        onClick = null,
    )
    if (pickerOpen && currencyCode != null) {
        CurrencyPickerDialog(
            codes = settings.supportedCurrencyCodes,
            selected = currencyCode,
            locale = locale,
            onSelect = { code ->
                pickerOpen = false
                onSelectCurrency(code)
            },
            onDismiss = { pickerOpen = false },
        )
    }
}

/**
 * A label and its value. They stack instead of sharing one line: at 200% text a long currency value (the
 * platform display name plus its ISO code) starved the label, which wrapped to four lines. A row without
 * [onClick] has no click action, which is what makes the units read-only rather than merely unlabelled.
 */
@Composable
private fun SettingsValueRow(
    labelResource: Int,
    value: String,
    rowTag: String,
    valueTag: String?,
    onClick: (() -> Unit)?,
) {
    val interaction =
        if (onClick == null) {
            Modifier.semantics(mergeDescendants = true) {}
        } else {
            Modifier.clickable(role = Role.Button, onClick = onClick)
        }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(interaction)
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .testTag(rowTag),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(labelResource),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = if (valueTag == null) Modifier else Modifier.testTag(valueTag),
        )
    }
}

/** The `SUPPORTED_CURRENCY_CODES` list, in contract order, each with its platform display name. */
@Composable
private fun CurrencyPickerDialog(
    codes: List<String>,
    selected: String,
    locale: Locale,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(SettingsTestTags.CURRENCY_PICKER),
        title = { Text(stringResource(R.string.settings_currency_picker_title), modifier = Modifier.screenHeading()) },
        text = {
            Column(modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                codes.forEach { code ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = code == selected,
                                    role = Role.RadioButton,
                                    onClick = { onSelect(code) },
                                ).padding(vertical = 12.dp)
                                .testTag(SettingsTestTags.currencyOption(code)),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = code == selected, onClick = null)
                        Text(stringResource(R.string.settings_currency_value, currencyDisplayName(code, locale), code))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag(SettingsTestTags.CURRENCY_PICKER_CANCEL),
            ) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

/** `E3-05` owns the classifier and the retry; Settings reuses the indicator unchanged (`D-193`). */
@Composable
private fun BackupSection(
    sync: SyncUiState,
    onRetry: () -> Unit,
) {
    SettingsSectionHeader(R.string.settings_section_backup)
    SyncStatusIndicator(
        status = sync.status,
        message = sync.message,
        onRetry = onRetry,
        modifier = Modifier.testTag(SettingsTestTags.BACKUP_ROW),
    )
}

/**
 * The `§16.1` opt-in, off by default and applied immediately by the shared holder. The copy says
 * pseudonymous, not anonymous (`D-203`).
 *
 * While the stored settings are unknown (`currencyCode == null`) the row shows a loading value and no
 * switch at all: an unchecked switch would claim a stored opt-out the app has not read, which is exactly
 * what `docs/CONTRACTS.md §20.10` prohibits. A known state disables the switch only while a write runs.
 */
@Composable
private fun PrivacySection(
    settings: SettingsUiState,
    onAnalyticsEnabledChange: (Boolean) -> Unit,
) {
    SettingsSectionHeader(R.string.settings_section_privacy)
    if (settings.currencyCode == null) {
        SettingsValueRow(
            labelResource = R.string.settings_analytics_title,
            value = stringResource(R.string.loading),
            rowTag = SettingsTestTags.ANALYTICS_LOADING,
            valueTag = null,
            onClick = null,
        )
        return
    }
    val enabled = !settings.isSaving
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .toggleable(
                    value = settings.analyticsEnabled,
                    enabled = enabled,
                    role = Role.Switch,
                    onValueChange = onAnalyticsEnabledChange,
                ).padding(horizontal = 20.dp, vertical = 16.dp)
                .testTag(SettingsTestTags.ANALYTICS_SWITCH),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_analytics_title),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(R.string.settings_analytics_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = settings.analyticsEnabled, onCheckedChange = null, enabled = enabled)
    }
}

/** The D-215 rows. They are disabled while a session operation is running. */
@Composable
private fun AccountSection(
    session: SessionUiState,
    callbacks: SettingsCallbacks,
) {
    val rows = settingsSessionRows(session.phase)
    if (rows.isEmpty()) return
    SettingsSectionHeader(R.string.settings_section_account)
    LinkAccountSection(
        session = session,
        onLinkAccount = callbacks.onLinkAccount,
    )
    if (session.isBusy) {
        val label = stringResource(R.string.loading)
        LinearProgressIndicator(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .semantics { contentDescription = label }
                    .testTag(SettingsTestTags.SESSION_PROGRESS),
        )
    }
    rows.forEach { row ->
        SessionActionRow(row = row, enabled = !session.isBusy, callbacks = callbacks)
    }
}

/**
 * D-215 maps both deletion rows to `requestDeleteAccount()` in the route: the shared holder decides
 * which `DELETING` operation the current owner is entitled to (`D-158`).
 */
@Composable
private fun SessionActionRow(
    row: SettingsSessionRow,
    enabled: Boolean,
    callbacks: SettingsCallbacks,
) {
    when (row) {
        SettingsSessionRow.SIGN_OUT -> {
            SettingsActionRow(
                labelResource = R.string.settings_sign_out,
                tag = SettingsTestTags.SIGN_OUT_ROW,
                destructive = false,
                enabled = enabled,
                onClick = callbacks.onSignOut,
            )
        }

        SettingsSessionRow.DELETE_ACCOUNT -> {
            SettingsActionRow(
                labelResource = R.string.settings_delete_account,
                tag = SettingsTestTags.DELETE_ACCOUNT_ROW,
                destructive = true,
                enabled = enabled,
                onClick = callbacks.onDeleteAccount,
            )
        }

        SettingsSessionRow.DELETE_LOCAL_DATA -> {
            SettingsActionRow(
                labelResource = R.string.settings_delete_local_data,
                tag = SettingsTestTags.DELETE_LOCAL_DATA_ROW,
                destructive = true,
                enabled = enabled,
                onClick = callbacks.onDeleteLocalData,
            )
        }
    }
}

@Composable
private fun SettingsActionRow(
    labelResource: Int,
    tag: String,
    destructive: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = stringResource(labelResource),
        style = MaterialTheme.typography.bodyLarge,
        color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .testTag(tag),
    )
}

/** Test tags of the Settings surface and of its departure prompts. Every value starts with `settings_`. */
internal object SettingsTestTags {
    const val ENTRY = "settings_entry"
    const val SCREEN = "settings_screen"
    const val LINK_ACCOUNT_ROW = "settings_link_account_row"
    const val LINK_ACCOUNT_GOOGLE = "settings_link_account_google"
    const val LINK_ACCOUNT_PROGRESS = "settings_link_account_progress"
    const val CONVERSION_DIALOG = "settings_conversion_dialog"
    const val CONVERSION_CONFIRM = "settings_conversion_confirm"
    const val CONVERSION_CANCEL = "settings_conversion_cancel"
    const val BACK = "settings_back"
    const val ERROR = "settings_error"
    const val DISMISS_ERROR = "settings_dismiss_error"
    const val CURRENCY_ROW = "settings_currency_row"
    const val CURRENCY_VALUE = "settings_currency_value"
    const val CURRENCY_PICKER = "settings_currency_picker"
    const val CURRENCY_PICKER_CANCEL = "settings_currency_picker_cancel"
    const val DISTANCE_UNIT_ROW = "settings_distance_unit_row"
    const val VOLUME_UNIT_ROW = "settings_volume_unit_row"
    const val BACKUP_ROW = "settings_backup_row"
    const val ANALYTICS_SWITCH = "settings_analytics_switch"
    const val ANALYTICS_LOADING = "settings_analytics_loading"
    const val SESSION_PROGRESS = "settings_session_progress"
    const val SIGN_OUT_ROW = "settings_sign_out_row"
    const val DELETE_ACCOUNT_ROW = "settings_delete_account_row"
    const val DELETE_LOCAL_DATA_ROW = "settings_delete_local_data_row"
    const val VERSION_ROW = "settings_version_row"
    const val VERSION_VALUE = "settings_version_value"
    const val PENDING_SYNC_DIALOG = "settings_pending_sync_dialog"
    const val PENDING_SYNC_BODY = "settings_pending_sync_body"
    const val PENDING_SYNC_WAIT = "settings_pending_sync_wait"
    const val PENDING_SYNC_CANCEL = "settings_pending_sync_cancel"
    const val PENDING_SYNC_DISCARD = "settings_pending_sync_discard"
    const val CONFIRM_DELETE_ACCOUNT_DIALOG = "settings_confirm_delete_account_dialog"
    const val CONFIRM_DELETE_LOCAL_DATA_DIALOG = "settings_confirm_delete_local_data_dialog"
    const val CONFIRM_DESTRUCTIVE = "settings_confirm_destructive"
    const val CANCEL_DESTRUCTIVE = "settings_cancel_destructive"
    const val SESSION_ERROR_DIALOG = "settings_session_error_dialog"
    const val SESSION_ERROR_TEXT = "settings_session_error_text"
    const val SESSION_ERROR_DISMISS = "settings_session_error_dismiss"

    fun currencyOption(code: String): String = "settings_currency_option_$code"
}
