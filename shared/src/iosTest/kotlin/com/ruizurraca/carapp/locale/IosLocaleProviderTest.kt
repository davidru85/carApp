package com.ruizurraca.carapp.locale

import platform.Foundation.NSLocale
import kotlin.test.Test
import kotlin.test.assertEquals

class IosLocaleProviderTest {
    @Test
    fun supportedTwoDecimalLocaleResolvesItsCurrencyCode() {
        val localeInfo = IosLocaleProvider { NSLocale(localeIdentifier = "en_US") }.current()

        assertEquals("USD", localeInfo.suggestedCurrency.value)
    }

    @Test
    fun currencyWithoutTwoFractionDigitsFallsBackToEur() {
        val localeInfo = IosLocaleProvider { NSLocale(localeIdentifier = "ja_JP") }.current()

        assertEquals("EUR", localeInfo.suggestedCurrency.value)
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
}
