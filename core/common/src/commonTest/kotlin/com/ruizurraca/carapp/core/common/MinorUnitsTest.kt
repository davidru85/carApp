package com.ruizurraca.carapp.core.common

import com.ruizurraca.carapp.core.model.CurrencyCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MinorUnitsTest {
    @Test
    fun everySupportedCurrencyHasFactorOneHundred() {
        SUPPORTED_CURRENCY_CODES.forEach { code ->
            assertEquals(
                100,
                MinorUnits.factorFor(CurrencyCode(code)),
                "docs/CONTRACTS.md §20.0.1: every supported MVP currency is two-decimal",
            )
        }
    }

    @Test
    fun unsupportedCurrenciesReturnNullRatherThanADefaultFactor() {
        // JPY has zero minor units and KWD has three; both are real ISO-4217 codes and both are
        // outside the MVP set, so they MUST return null rather than a guessed factor.
        listOf("JPY", "KWD", "XXX", "", "eur").forEach { code ->
            assertNull(
                MinorUnits.factorFor(CurrencyCode(code)),
                "Unsupported code '$code' must not resolve to a factor",
            )
        }
    }

    @Test
    fun theSupportedSetIsExactlyTheOneInTheContract() {
        assertEquals(21, SUPPORTED_CURRENCY_CODES.size)
        assertEquals(
            listOf(
                "ARS",
                "AUD",
                "BRL",
                "CAD",
                "CHF",
                "COP",
                "CZK",
                "DKK",
                "EUR",
                "GBP",
                "HUF",
                "MAD",
                "MXN",
                "NOK",
                "NZD",
                "PEN",
                "PLN",
                "RON",
                "SEK",
                "USD",
                "UYU",
            ),
            SUPPORTED_CURRENCY_CODES.sorted(),
        )
    }
}
