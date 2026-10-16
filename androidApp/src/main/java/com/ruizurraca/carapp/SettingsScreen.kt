package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.model.DistanceUnit
import com.ruizurraca.carapp.core.model.VolumeUnit
import java.util.Currency
import java.util.Locale

/**
 * The session rows a phase offers (B1-D5). `UNKNOWN`, `SIGNED_OUT` and `DELETING` have no owner the
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
 * (`docs/CONTRACTS.md §15.3`, B1-D1), so the host produces the name through the platform currency API.
 * A code the platform does not know is shown as itself instead of crashing the screen.
 */
internal fun currencyDisplayName(
    code: String,
    locale: Locale,
): String = runCatching { Currency.getInstance(code).getDisplayName(locale) }.getOrDefault(code)
