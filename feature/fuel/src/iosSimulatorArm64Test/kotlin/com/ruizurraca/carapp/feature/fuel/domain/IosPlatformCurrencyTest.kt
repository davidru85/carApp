package com.ruizurraca.carapp.feature.fuel.domain

import com.ruizurraca.carapp.core.common.SUPPORTED_CURRENCY_CODES
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterCurrencyStyle
import kotlin.test.Test
import kotlin.test.assertEquals

class IosPlatformCurrencyTest {
    @Test
    fun everySupportedCurrencyResolvesToTwoMinorDigitsOrEurFallback() {
        SUPPORTED_CURRENCY_CODES.forEach { code ->
            val resolved = code.takeIf { fractionDigits(it) == TWO_MINOR_DIGITS } ?: FALLBACK_CURRENCY

            assertEquals(
                TWO_MINOR_DIGITS,
                fractionDigits(resolved),
                "$code resolved to $resolved",
            )
        }
    }

    private fun fractionDigits(currencyCode: String): Int =
        NSNumberFormatter()
            .apply {
                numberStyle = NSNumberFormatterCurrencyStyle
                this.currencyCode = currencyCode
            }.maximumFractionDigits
            .toInt()

    private companion object {
        const val TWO_MINOR_DIGITS = 2
        const val FALLBACK_CURRENCY = "EUR"
    }
}
