package com.ruizurraca.carapp.locale

import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleCurrencyCode
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterCurrencyStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class IosLocaleProviderTest {
    @Test
    fun supportedTwoDecimalLocaleResolvesItsCurrencyCode() {
        val localeInfo = IosLocaleProvider { NSLocale(localeIdentifier = "en_US") }.current()

        assertEquals("USD", localeInfo.suggestedCurrency.value)
    }

    @Test
    fun localeCurrencyOutsideTheMvpSetFallsBackToEur() {
        val localeInfo = IosLocaleProvider { NSLocale(localeIdentifier = "ja_JP") }.current()

        assertEquals("ja-JP", localeInfo.languageTag)
        assertEquals("JP", localeInfo.region)
        assertEquals("EUR", localeInfo.suggestedCurrency.value)
    }

    @Test
    fun foundationCurrencyFractionDigitsMatchTheMvpPremise() {
        assertEquals(2, maximumFractionDigits("USD"))
        assertNotEquals(2, maximumFractionDigits("JPY"))
    }

    @Test
    fun languageTagComesFromTheFoundationLocaleIdentifier() {
        val localeInfo = IosLocaleProvider { NSLocale(localeIdentifier = "es_ES") }.current()

        assertEquals("es-ES", localeInfo.languageTag)
    }

    @Test
    fun regionComesFromTheFoundationCountryCode() {
        val localeInfo = IosLocaleProvider { NSLocale(localeIdentifier = "en_US") }.current()

        assertEquals("US", localeInfo.region)
    }

    @Test
    fun languageOnlyLocaleProvidesNullRegionAndFallsBackToEur() {
        val locale = NSLocale(localeIdentifier = "es")

        assertNull(locale.objectForKey(NSLocaleCurrencyCode))

        val localeInfo = IosLocaleProvider { locale }.current()

        assertEquals("es", localeInfo.languageTag)
        assertNull(localeInfo.region)
        assertEquals("EUR", localeInfo.suggestedCurrency.value)
    }

    private fun maximumFractionDigits(currencyCode: String): Int =
        NSNumberFormatter()
            .apply {
                numberStyle = NSNumberFormatterCurrencyStyle
                this.currencyCode = currencyCode
            }.maximumFractionDigits
            .toInt()
}
