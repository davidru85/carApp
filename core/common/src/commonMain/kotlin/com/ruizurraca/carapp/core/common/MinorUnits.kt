package com.ruizurraca.carapp.core.common

import com.ruizurraca.carapp.core.model.CurrencyCode

/**
 * ISO-4217 minor-unit factors for the MVP currency set (`docs/CONTRACTS.md §2`, `§20.3`).
 *
 * Returns `100` for the codes in [SUPPORTED_CURRENCY_CODES] and `null` for every other code.
 * Validation resolves the factor and rejects an unsupported currency before any monetary
 * arithmetic runs, which is why the arithmetic in `:core:model` takes the factor as a parameter
 * and never looks it up.
 *
 * A locale suggesting a code outside the set falls back to `EUR`; an explicit user selection
 * outside the set is a `ValidationError.InvalidUnit`.
 */
object MinorUnits {
    fun factorFor(currency: CurrencyCode): Int? =
        if (currency.value in SUPPORTED_CURRENCY_CODES) TWO_DECIMAL_FACTOR else null

    private const val TWO_DECIMAL_FACTOR = 100
}
