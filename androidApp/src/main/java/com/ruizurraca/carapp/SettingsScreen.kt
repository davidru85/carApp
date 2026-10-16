package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.model.DistanceUnit
import com.ruizurraca.carapp.core.model.VolumeUnit
import java.util.Locale

/**
 * The session rows of the Settings account section (B1-D5). A permanent owner can sign out or delete
 * the account; a local or anonymous owner has one "delete local data" row, because its identity
 * exists only on this device (`D-156`, `docs/CONTRACTS.md §11.5`).
 */
internal enum class SettingsSessionRow { SIGN_OUT, DELETE_ACCOUNT, DELETE_LOCAL_DATA }

/** RED stub: the `E4-01` GREEN commit replaces the body. */
@Suppress("UnusedParameter")
internal fun settingsSessionRows(phase: SessionPhase): List<SettingsSessionRow> = emptyList()

/** RED stub: the `E4-01` GREEN commit replaces the body. */
@Suppress("UnusedParameter", "FunctionOnlyReturningConstant")
internal fun distanceUnitLabelResource(unit: DistanceUnit): Int = 0

/** RED stub: the `E4-01` GREEN commit replaces the body. */
@Suppress("UnusedParameter", "FunctionOnlyReturningConstant")
internal fun volumeUnitLabelResource(unit: VolumeUnit): Int = 0

/** RED stub: the `E4-01` GREEN commit replaces the body. */
@Suppress("UnusedParameter", "FunctionOnlyReturningConstant")
internal fun currencyDisplayName(
    code: String,
    locale: Locale,
): String = ""
