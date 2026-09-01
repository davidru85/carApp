package com.ruizurraca.carapp.core.common

import com.ruizurraca.carapp.core.model.CurrencyCode
import kotlin.test.Test
import kotlin.test.assertEquals

class LocaleCurrencyTest {
    @Test
    fun supportedCurrencyWithRuntimeFactorOneHundredIsRetained() {
        assertEquals(
            CurrencyCode("USD"),
            resolveLocaleCurrency(CurrencyCode("USD"), runtimeMinorUnitFactor = 100),
        )
    }

    @Test
    fun supportedCurrencyWithDifferentRuntimeFactorFallsBackToEur() {
        assertEquals(
            CurrencyCode("EUR"),
            resolveLocaleCurrency(CurrencyCode("USD"), runtimeMinorUnitFactor = 1),
        )
    }

    @Test
    fun unsupportedOrMissingPlatformCurrencyFallsBackToEur() {
        listOf(CurrencyCode("JPY"), null).forEach { suggested ->
            assertEquals(
                CurrencyCode("EUR"),
                resolveLocaleCurrency(suggested, runtimeMinorUnitFactor = 100),
            )
        }
    }

    @Test
    fun missingRuntimeFactorFallsBackToEur() {
        assertEquals(
            CurrencyCode("EUR"),
            resolveLocaleCurrency(CurrencyCode("USD"), runtimeMinorUnitFactor = null),
        )
    }
}
