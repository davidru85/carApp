package com.ruizurraca.carapp.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * `docs/CONTRACTS.md §2` states that the divisions are total because the caller guarantees a
 * positive denominator: a segment with `distanceKm <= 0` is `SegmentResult.Invalid` and never
 * reaches the arithmetic.
 *
 * These tests pin the guard that makes the guarantee visible. Without them a caller bug would
 * surface as a division by zero deep inside a sync cycle rather than at the call site.
 */
class ArithmeticGuardsTest {
    @Test
    fun consumptionRejectsANonPositiveDistance() {
        listOf(0L, -1L).forEach { distance ->
            assertFailsWith<IllegalArgumentException> { segmentConsumptionScaledOf(40_000L, distance) }
            assertFailsWith<IllegalArgumentException> { averageConsumptionScaledOf(40_000L, distance) }
        }
    }

    @Test
    fun derivingAPriceRejectsAZeroDenominator() {
        assertFailsWith<IllegalArgumentException> { pricePerLiterScaledOf(6_000L, 0L, 100) }
        assertFailsWith<IllegalArgumentException> { pricePerLiterScaledOf(6_000L, 40_000L, 0) }
    }

    @Test
    fun derivingLitresRejectsAZeroDenominator() {
        assertFailsWith<IllegalArgumentException> { litersScaledOf(6_000L, 0L, 100) }
        assertFailsWith<IllegalArgumentException> { litersScaledOf(6_000L, 1_500L, 0) }
    }

    @Test
    fun moneyCarriesItsAmountAndCurrencyWithoutInterpretingThem() {
        val money = Money(minorUnits = 8_073L, currency = CurrencyCode("EUR"))

        assertEquals(8_073L, money.minorUnits)
        assertEquals("EUR", money.currency.value)
        assertEquals(money, Money(8_073L, CurrencyCode("EUR")))
        assertEquals(
            false,
            money == Money(8_073L, CurrencyCode("USD")),
            "docs/CONTRACTS.md §2: values of different currency are never equal",
        )
    }
}
