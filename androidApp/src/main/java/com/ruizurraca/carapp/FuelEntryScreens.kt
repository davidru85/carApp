package com.ruizurraca.carapp

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ruizurraca.carapp.core.common.Confirmation
import com.ruizurraca.carapp.core.common.UiMessage
import com.ruizurraca.carapp.core.model.ConsumptionInvalidReason
import com.ruizurraca.carapp.feature.fuel.presentation.FuelEntryFormStateHolder
import com.ruizurraca.carapp.feature.fuel.presentation.FuelEntryFormUiState
import com.ruizurraca.carapp.feature.fuel.presentation.FuelEntryListItemUi
import com.ruizurraca.carapp.feature.fuel.presentation.FuelEntryListUiState
import com.ruizurraca.carapp.feature.fuel.presentation.MoneyInputMode
import kotlinx.coroutines.flow.collect
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
internal fun FuelEntryListContent(
    state: FuelEntryListUiState,
    onEdit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val calendarDay = rememberDeviceCalendarDay()
    when {
        state.isLoading && state.entries.isEmpty() -> {
            Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
        }

        state.message != null && state.entries.isEmpty() -> {
            FuelEntryErrorText(state.message, modifier)
        }

        else -> {
            LazyColumn(
                modifier = modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    ConsumptionSummary(state)
                }
                if (state.entries.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.add_first_fuel_entry_invitation),
                            modifier = Modifier.testTag(VehicleTestTags.FIRST_FUEL_INVITATION),
                        )
                    }
                } else {
                    items(state.entries, key = FuelEntryListItemUi::id) { entry ->
                        FuelEntryRow(entry, calendarDay, onEdit)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConsumptionSummary(state: FuelEntryListUiState) {
    val average = state.consumptionAverageScaled
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(stringResource(R.string.consumption_average_title), style = MaterialTheme.typography.labelLarge)
        if (average == null) {
            Text(
                text = stringResource(R.string.consumption_empty),
                modifier = Modifier.testTag(FuelEntryTestTags.CONSUMPTION_EMPTY),
            )
        } else {
            Text(
                text =
                    stringResource(
                        R.string.consumption_value,
                        formatScaled(average, CONSUMPTION_SCALE),
                    ),
                style = MaterialTheme.typography.displayMedium,
                modifier = Modifier.testTag(FuelEntryTestTags.CONSUMPTION_AVERAGE),
            )
            Text(
                stringResource(
                    if (state.isConsumptionReliable) {
                        R.string.consumption_reliable
                    } else {
                        R.string.consumption_not_yet_reliable
                    },
                    state.validConsumptionSegmentCount,
                ),
            )
        }
    }
}

@Composable
private fun FuelEntryRow(
    entry: FuelEntryListItemUi,
    calendarDay: FuelEntryCalendarDay,
    onEdit: (String) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onEdit(entry.id) }
                .padding(vertical = 12.dp)
                .testTag(FuelEntryTestTags.row(entry.id)),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(calendarDay.format(entry.dateEpochMillis), style = MaterialTheme.typography.titleMedium)
            Text(
                entry.consumptionScaled?.let { value ->
                    stringResource(R.string.consumption_value, formatScaled(value, CONSUMPTION_SCALE))
                } ?: stringResource(entry.invalidReason.explanationResource()),
                modifier = Modifier.testTag(FuelEntryTestTags.explanation(entry.id)),
            )
        }
        Text(
            stringResource(
                R.string.fuel_entry_facts,
                entry.odometerKm,
                formatScaled(entry.litersScaled, FUEL_SCALE),
                formatScaled(entry.totalCostMinor, MINOR_UNIT_SCALE),
                entry.currencyCode,
            ),
        )
        if (!entry.isFullTank) FuelIndicator(R.string.partial_tank, entry.id, "partial")
        if (entry.hasMissedEntries) FuelIndicator(R.string.missed_entries, entry.id, "missed")
        if (entry.odometerInconsistent) FuelIndicator(R.string.inconsistent_odometer, entry.id, "odometer")
        HorizontalDivider()
    }
}

@Composable
private fun FuelIndicator(
    textResource: Int,
    entryId: String,
    indicator: String,
) {
    Text(
        text = stringResource(textResource),
        color = MaterialTheme.colorScheme.tertiary,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.testTag(FuelEntryTestTags.indicator(entryId, indicator)),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FuelEntryFormScreen(
    stateHolder: FuelEntryFormStateHolder,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val state by stateHolder.state.collectAsState()
    val calendarDay = rememberDeviceCalendarDay()

    LaunchedEffect(stateHolder) {
        stateHolder.observeSaveCompletions().collect { onSaved() }
    }

    if (state.message?.confirmation == Confirmation.OdometerInconsistent) {
        FuelOdometerWarningDialog(
            onDismiss = stateHolder::clearMessage,
            onConfirm = {
                stateHolder.confirmSave(Confirmation.OdometerInconsistent)
            },
        )
    }

    Scaffold(
        topBar = {
            FuelEntryFormTopBar(
                state = state,
                onBack = onBack,
                onSave = stateHolder::save,
            )
        },
    ) { padding ->
        FuelEntryForm(
            state = state,
            stateHolder = stateHolder,
            calendarDay = calendarDay,
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
private fun FuelOdometerWarningDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(FuelEntryTestTags.ODOMETER_WARNING),
        title = { Text(stringResource(R.string.odometer_warning_title)) },
        text = { Text(stringResource(R.string.odometer_warning_body)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag(FuelEntryTestTags.CONFIRM_ODOMETER_WARNING),
            ) {
                Text(stringResource(R.string.save_anyway))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FuelEntryFormTopBar(
    state: FuelEntryFormUiState,
    onBack: () -> Unit,
    onSave: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                stringResource(
                    if (state.entryId == null) R.string.create_fuel_entry_title else R.string.edit_fuel_entry_title,
                ),
            )
        },
        navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.close)) } },
        actions = {
            TextButton(
                onClick = onSave,
                enabled = !state.isSaving,
                modifier = Modifier.testTag(FuelEntryTestTags.SAVE),
            ) {
                Text(stringResource(R.string.save_fuel_entry))
            }
        },
    )
}

@Composable
private fun FuelEntryForm(
    state: FuelEntryFormUiState,
    stateHolder: FuelEntryFormStateHolder,
    calendarDay: FuelEntryCalendarDay,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FuelDateField(state.dateEpochMillis, calendarDay, stateHolder::setDateEpochMillis)
        FuelOdometerField(state.odometerKm, stateHolder::setOdometerKm)
        Text(stringResource(R.string.money_input_method), style = MaterialTheme.typography.labelLarge)
        MoneyModeSelector(state.moneyInputMode, stateHolder::setMoneyInputMode)
        MoneyInputs(state, stateHolder)
        FuelToggle(
            state.isFullTank,
            R.string.full_tank,
            R.string.full_tank_help,
            FuelEntryTestTags.FULL_TANK,
            stateHolder::setFullTank,
        )
        FuelToggle(
            state.hasMissedEntries,
            R.string.missed_entries,
            R.string.missed_entries_help,
            FuelEntryTestTags.MISSED_ENTRIES,
            stateHolder::setMissedEntries,
        )
        OutlinedTextField(
            value = state.notes.orEmpty(),
            onValueChange = { value -> stateHolder.setNotes(value.ifBlank { null }) },
            modifier = Modifier.fillMaxWidth().testTag(FuelEntryTestTags.NOTES),
            label = { Text(stringResource(R.string.notes_optional)) },
        )
        state.message?.takeIf { it.confirmation == null }?.let { message ->
            FuelEntryErrorText(message)
        }
    }
}

@Composable
private fun FuelDateField(
    dateEpochMillis: Long,
    calendarDay: FuelEntryCalendarDay,
    onSelected: (Long) -> Unit,
) {
    val context = LocalContext.current
    OutlinedButton(
        onClick = {
            val selected = calendarDay.localDate(dateEpochMillis)
            DatePickerDialog(
                context,
                { _, year, month, day -> onSelected(calendarDay.atStartOfDay(year, month, day)) },
                selected.year,
                selected.monthValue - 1,
                selected.dayOfMonth,
            ).show()
        },
        modifier = Modifier.fillMaxWidth().testTag(FuelEntryTestTags.DATE),
    ) {
        Text(stringResource(R.string.fuel_date_value, calendarDay.format(dateEpochMillis)))
    }
}

@Composable
private fun FuelOdometerField(
    odometerKm: Long,
    onChanged: (Long) -> Unit,
) {
    OutlinedTextField(
        value = odometerKm.toString(),
        onValueChange = { value -> value.toLongOrNull()?.let(onChanged) },
        modifier = Modifier.fillMaxWidth().testTag(FuelEntryTestTags.ODOMETER),
        label = { Text(stringResource(R.string.current_odometer)) },
        suffix = { Text(stringResource(R.string.kilometer_unit)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
    )
}

@Composable
private fun FuelToggle(
    checked: Boolean,
    labelResource: Int,
    helpResource: Int,
    testTag: String,
    onChanged: (Boolean) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(labelResource))
            Text(stringResource(helpResource), style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChanged,
            modifier = Modifier.testTag(testTag),
        )
    }
}

@Composable
private fun MoneyModeSelector(
    selected: MoneyInputMode,
    onSelected: (MoneyInputMode) -> Unit,
) {
    MoneyInputMode.entries.forEach { mode ->
        OutlinedButton(
            onClick = { onSelected(mode) },
            modifier = Modifier.fillMaxWidth().testTag(FuelEntryTestTags.mode(mode)),
        ) {
            val label = stringResource(mode.labelResource())
            Text(if (mode == selected) stringResource(R.string.selected_money_mode, label) else label)
        }
    }
}

@Composable
private fun MoneyInputs(
    state: FuelEntryFormUiState,
    stateHolder: FuelEntryFormStateHolder,
) {
    when (state.moneyInputMode) {
        MoneyInputMode.LITERS_AND_PRICE -> {
            ScaledMoneyInput(
                value = state.litersScaled,
                scale = FUEL_SCALE,
                label = R.string.liters,
                tag = FuelEntryTestTags.LITERS,
                onValue = stateHolder::setLitersScaled,
            )
            ScaledMoneyInput(
                value = state.pricePerLiterScaled,
                scale = PRICE_SCALE,
                label = R.string.price_per_liter,
                tag = FuelEntryTestTags.PRICE_PER_LITER,
                onValue = stateHolder::setPricePerLiterScaled,
            )
            ComputedMoneyValue(R.string.calculated_total, state.totalCostMinor, MINOR_UNIT_SCALE, state.currencyCode)
        }

        MoneyInputMode.LITERS_AND_TOTAL -> {
            ScaledMoneyInput(
                value = state.litersScaled,
                scale = FUEL_SCALE,
                label = R.string.liters,
                tag = FuelEntryTestTags.LITERS,
                onValue = stateHolder::setLitersScaled,
            )
            ScaledMoneyInput(
                value = state.totalCostMinor,
                scale = MINOR_UNIT_SCALE,
                label = R.string.total_cost,
                tag = FuelEntryTestTags.TOTAL_COST,
                onValue = stateHolder::setTotalCostMinor,
            )
            ComputedMoneyValue(R.string.calculated_price, state.pricePerLiterScaled, PRICE_SCALE, state.currencyCode)
        }

        MoneyInputMode.PRICE_AND_TOTAL -> {
            ScaledMoneyInput(
                value = state.pricePerLiterScaled,
                scale = PRICE_SCALE,
                label = R.string.price_per_liter,
                tag = FuelEntryTestTags.PRICE_PER_LITER,
                onValue = stateHolder::setPricePerLiterScaled,
            )
            ScaledMoneyInput(
                value = state.totalCostMinor,
                scale = MINOR_UNIT_SCALE,
                label = R.string.total_cost,
                tag = FuelEntryTestTags.TOTAL_COST,
                onValue = stateHolder::setTotalCostMinor,
            )
            ComputedMoneyValue(R.string.calculated_liters, state.litersScaled, FUEL_SCALE, state.currencyCode)
        }
    }
}

@Composable
private fun ScaledMoneyInput(
    value: Long?,
    scale: Int,
    label: Int,
    tag: String,
    onValue: (Long?) -> Unit,
) {
    OutlinedTextField(
        value = value?.let { formatScaled(it, scale) }.orEmpty(),
        onValueChange = { input -> onValue(parseScaled(input, scale)) },
        modifier = Modifier.fillMaxWidth().testTag(tag),
        label = { Text(stringResource(label)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
    )
}

@Composable
private fun ComputedMoneyValue(
    label: Int,
    value: Long?,
    scale: Int,
    currencyCode: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
    ) {
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium)
        Text(
            value?.let { stringResource(R.string.money_value, formatScaled(it, scale), currencyCode) }
                ?: stringResource(R.string.value_pending),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.testTag(FuelEntryTestTags.DERIVED_VALUE),
        )
    }
}

@Composable
private fun FuelEntryErrorText(
    message: UiMessage?,
    modifier: Modifier = Modifier,
) {
    if (message == null) return
    Text(
        text = stringResource(message.fuelStringResource()),
        color = MaterialTheme.colorScheme.error,
        modifier = modifier.testTag(FuelEntryTestTags.ERROR),
    )
}

private fun UiMessage.fuelStringResource(): Int =
    when (code) {
        "VALIDATION.FUTURE_DATE" -> R.string.error_future_date
        "VALIDATION.INVALID_MONEY_INPUT" -> R.string.error_money_input
        "VALIDATION.INVALID_UNIT" -> R.string.error_currency
        else -> stringResource()
    }

private fun ConsumptionInvalidReason?.explanationResource(): Int =
    when (this) {
        ConsumptionInvalidReason.NoPreviousFullTank -> R.string.consumption_no_previous_full_tank
        ConsumptionInvalidReason.EndEntryNotFullTank -> R.string.consumption_partial_tank
        ConsumptionInvalidReason.MissedEntriesInSegment -> R.string.consumption_missed_entries
        ConsumptionInvalidReason.InconsistentOdometerInSegment -> R.string.consumption_inconsistent_odometer
        ConsumptionInvalidReason.NonPositiveDistance -> R.string.consumption_non_positive_distance
        ConsumptionInvalidReason.DuplicateOdometerInSegment -> R.string.consumption_duplicate_odometer
        null -> R.string.consumption_unavailable
    }

private fun MoneyInputMode.labelResource(): Int =
    when (this) {
        MoneyInputMode.LITERS_AND_PRICE -> R.string.money_mode_liters_price
        MoneyInputMode.LITERS_AND_TOTAL -> R.string.money_mode_liters_total
        MoneyInputMode.PRICE_AND_TOTAL -> R.string.money_mode_price_total
    }

@Composable
private fun rememberDeviceCalendarDay(): FuelEntryCalendarDay {
    val zoneId = remember { ZoneId.systemDefault() }
    val locale = remember { Locale.getDefault() }
    return remember(zoneId, locale) { FuelEntryCalendarDay(zoneId, locale) }
}

internal class FuelEntryCalendarDay(
    private val zoneId: ZoneId,
    locale: Locale,
) {
    private val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)

    fun format(epochMillis: Long): String = formatter.format(localDate(epochMillis))

    fun localDate(epochMillis: Long): LocalDate = Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDate()

    fun atStartOfDay(
        year: Int,
        zeroBasedMonth: Int,
        dayOfMonth: Int,
    ): Long =
        LocalDate
            .of(year, zeroBasedMonth + 1, dayOfMonth)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
}

internal fun parseScaled(
    input: String,
    scale: Int,
): Long? {
    val normalised = input.trim().replace(',', '.')
    val parts = normalised.split('.')
    if (!isValidScaledInput(parts, scale)) return null
    val whole = parts[0].toLongOrNull()
    val fractionText = parts.getOrElse(1) { "" }
    val factor = powerOfTen(scale)
    if (whole == null || factor == null) return null
    val fraction = fractionText.padEnd(scale, '0').toLongOrNull() ?: 0L
    return runCatching { Math.addExact(Math.multiplyExact(whole, factor), fraction) }.getOrNull()
}

private fun isValidScaledInput(
    parts: List<String>,
    scale: Int,
): Boolean =
    parts.size <= MAX_DECIMAL_PARTS &&
        parts[0].isNotEmpty() &&
        parts.all { part -> part.all(Char::isDigit) } &&
        parts.getOrElse(1) { "" }.length <= scale

internal fun formatScaled(
    value: Long,
    scale: Int,
): String {
    val factor = requireNotNull(powerOfTen(scale))
    val whole = value / factor
    val fraction = (value % factor).toString().padStart(scale, '0')
    return "$whole.$fraction"
}

private fun powerOfTen(scale: Int): Long? =
    when (scale) {
        MINOR_UNIT_SCALE -> MINOR_UNIT_FACTOR

        FUEL_SCALE,
        PRICE_SCALE,
        -> SCALED_VALUE_FACTOR

        else -> null
    }

private const val MAX_DECIMAL_PARTS = 2
private const val MINOR_UNIT_FACTOR = 100L
private const val SCALED_VALUE_FACTOR = 1_000L

object FuelEntryTestTags {
    const val ADD_FUEL_ENTRY = "add_fuel_entry"
    const val CONSUMPTION_EMPTY = "consumption_empty"
    const val CONSUMPTION_AVERAGE = "consumption_average"
    const val DATE = "fuel_date"
    const val ODOMETER = "fuel_odometer"
    const val LITERS = "fuel_liters"
    const val PRICE_PER_LITER = "fuel_price_per_liter"
    const val TOTAL_COST = "fuel_total_cost"
    const val DERIVED_VALUE = "fuel_derived_value"
    const val FULL_TANK = "fuel_full_tank"
    const val MISSED_ENTRIES = "fuel_missed_entries"
    const val NOTES = "fuel_notes"
    const val SAVE = "save_fuel_entry"
    const val ERROR = "fuel_error"
    const val ODOMETER_WARNING = "fuel_odometer_warning"
    const val CONFIRM_ODOMETER_WARNING = "confirm_fuel_odometer_warning"

    fun mode(mode: MoneyInputMode): String = "fuel_mode_${mode.name.lowercase()}"

    fun row(entryId: String): String = "fuel_row_$entryId"

    fun explanation(entryId: String): String = "fuel_explanation_$entryId"

    fun indicator(
        entryId: String,
        indicator: String,
    ): String = "fuel_indicator_${entryId}_$indicator"
}

private const val MINOR_UNIT_SCALE = 2
private const val FUEL_SCALE = 3
private const val PRICE_SCALE = 3
private const val CONSUMPTION_SCALE = 2
