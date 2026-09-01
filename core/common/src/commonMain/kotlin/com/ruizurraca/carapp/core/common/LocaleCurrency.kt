package com.ruizurraca.carapp.core.common

import com.ruizurraca.carapp.core.model.CurrencyCode
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

/** Resolves a platform-reported locale currency under the MVP two-decimal constraint. */
@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
fun resolveLocaleCurrency(
    suggestedCurrency: CurrencyCode?,
    runtimeMinorUnitFactor: Int?,
): CurrencyCode =
    suggestedCurrency
        ?.takeIf {
            runtimeMinorUnitFactor == TWO_DECIMAL_MINOR_UNIT_FACTOR &&
                MinorUnits.factorFor(it) == TWO_DECIMAL_MINOR_UNIT_FACTOR
        }
        ?: DEFAULT_CURRENCY

private val DEFAULT_CURRENCY = CurrencyCode("EUR")
private const val TWO_DECIMAL_MINOR_UNIT_FACTOR = 100
