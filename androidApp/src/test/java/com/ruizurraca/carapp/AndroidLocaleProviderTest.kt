package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.model.CurrencyCode
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class AndroidLocaleProviderTest {
    @Test
    fun currentLocaleProvidesLanguageRegionAndSupportedCurrency() {
        val locale = AndroidLocaleProvider { Locale.US }.current()

        assertEquals("en-US", locale.languageTag)
        assertEquals("US", locale.region)
        assertEquals(CurrencyCode("USD"), locale.suggestedCurrency)
    }

    @Test
    fun localeCurrencyOutsideTheMvpSetFallsBackToEur() {
        val locale = AndroidLocaleProvider { Locale.JAPAN }.current()

        assertEquals("ja-JP", locale.languageTag)
        assertEquals("JP", locale.region)
        assertEquals(CurrencyCode("EUR"), locale.suggestedCurrency)
    }
}
