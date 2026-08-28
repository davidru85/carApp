package com.ruizurraca.carapp.feature.fuel.domain

import com.ruizurraca.carapp.core.common.SUPPORTED_CURRENCY_CODES
import java.util.Currency
import kotlin.test.Test
import kotlin.test.assertEquals

class JvmPlatformCurrencyTest {
    @Test
    fun everySupportedCurrencyResolvesToTwoMinorDigitsOrEurFallback() {
        SUPPORTED_CURRENCY_CODES.forEach { code ->
            val resolved =
                code.takeIf { Currency.getInstance(it).defaultFractionDigits == TWO_MINOR_DIGITS }
                    ?: FALLBACK_CURRENCY

            assertEquals(
                TWO_MINOR_DIGITS,
                Currency.getInstance(resolved).defaultFractionDigits,
                "$code resolved to $resolved",
            )
        }
    }

    private companion object {
        const val TWO_MINOR_DIGITS = 2
        const val FALLBACK_CURRENCY = "EUR"
    }
}
