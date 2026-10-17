package com.ruizurraca.carapp

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `E4-02` (`D-222`): the display locale chooses separators, grouping and the currency placement,
 * and nothing else; the scaled value and its scale stay the `docs/CONTRACTS.md §2` representation.
 *
 * Grouped expectations use five or more integer digits on purpose: Spanish CLDR data leaves a
 * four-digit integer ungrouped on ICU-based runtimes such as iOS, while the JDK groups it, so a
 * four-digit expectation would describe the test JVM rather than the product.
 */
class LocaleFormattingTest {
    @Test
    fun spanishDecimalUsesTheCommaSeparator() {
        assertEquals("7,24", formatScaledDecimal(value = 724L, scale = 2, locale = SPANISH))
    }

    @Test
    fun englishDecimalUsesThePointSeparator() {
        assertEquals("7.24", formatScaledDecimal(value = 724L, scale = 2, locale = ENGLISH))
    }

    @Test
    fun spanishDecimalGroupsThousandsWithPoints() {
        assertEquals("12.345,678", formatScaledDecimal(value = 12_345_678L, scale = 3, locale = SPANISH))
    }

    @Test
    fun englishDecimalGroupsThousandsWithCommas() {
        assertEquals("12,345.678", formatScaledDecimal(value = 12_345_678L, scale = 3, locale = ENGLISH))
    }

    @Test
    fun decimalShowsEveryFractionDigitOfTheScale() {
        assertEquals("45.200", formatScaledDecimal(value = 45_200L, scale = 3, locale = ENGLISH))
    }

    @Test
    fun spanishInputTextUsesTheCommaWithoutGrouping() {
        assertEquals("1234,567", formatScaledInput(value = 1_234_567L, scale = 3, locale = SPANISH))
    }

    @Test
    fun englishInputTextUsesThePointWithoutGrouping() {
        assertEquals("1234.567", formatScaledInput(value = 1_234_567L, scale = 3, locale = ENGLISH))
    }

    @Test
    fun inputTextRoundTripsThroughTheParser() {
        assertEquals(1_549L, parseScaled(formatScaledInput(value = 1_549L, scale = 3, locale = SPANISH), scale = 3))
    }

    @Test
    fun inputTextKeepsThePointWhenTheLocaleSeparatorIsNeitherPointNorComma() {
        assertEquals("1.549", formatScaledInput(value = 1_549L, scale = 3, locale = ARABIC_EGYPT))
    }

    @Test
    fun spanishMoneyPlacesTheEuroAfterTheAmount() {
        assertEquals(
            "12.345,67 €",
            formatCurrencyAmount(value = 1_234_567L, scale = 2, currencyCode = "EUR", locale = SPANISH),
        )
    }

    @Test
    fun englishMoneyPlacesTheEuroBeforeTheAmount() {
        assertEquals(
            "€12,345.67",
            formatCurrencyAmount(value = 1_234_567L, scale = 2, currencyCode = "EUR", locale = ENGLISH),
        )
    }

    @Test
    fun englishDollarAmountUsesTheDollarSign() {
        assertEquals(
            "\$12,345.67",
            formatCurrencyAmount(value = 1_234_567L, scale = 2, currencyCode = "USD", locale = ENGLISH),
        )
    }

    @Test
    fun pricePerLitreKeepsThreeFractionDigits() {
        assertEquals(
            "1,549 €",
            formatCurrencyAmount(value = 1_549L, scale = 3, currencyCode = "EUR", locale = SPANISH),
        )
    }

    @Test
    fun unknownCurrencyCodeFollowsThePlainAmount() {
        assertEquals(
            "61.96 ZZZ",
            formatCurrencyAmount(value = 6_196L, scale = 2, currencyCode = "ZZZ", locale = ENGLISH),
        )
    }

    @Test
    fun spanishIntegerGroupsThousandsWithPoints() {
        assertEquals("142.500", formatInteger(value = 142_500L, locale = SPANISH))
    }

    @Test
    fun englishIntegerGroupsThousandsWithCommas() {
        assertEquals("142,500", formatInteger(value = 142_500L, locale = ENGLISH))
    }

    private companion object {
        val SPANISH: Locale = Locale.forLanguageTag("es-ES")
        val ENGLISH: Locale = Locale.forLanguageTag("en-US")
        val ARABIC_EGYPT: Locale = Locale.forLanguageTag("ar-EG")
    }
}
