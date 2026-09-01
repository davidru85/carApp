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
    error(
        "E1-10 locale currency resolution is not implemented: " +
            "$suggestedCurrency/$runtimeMinorUnitFactor",
    )
