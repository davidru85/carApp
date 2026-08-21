package com.ruizurraca.carapp.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

/** Golden values of `docs/CONTRACTS.md §2`. Every row of that table MUST be covered. */
class MonetaryArithmeticTest {
    private val eurFactor = 100

    @Test
    fun totalCostRoundsHalfUpOnTheDocumentedGoldenValue() {
        // 45.123 L at 1.789 EUR/L; exact value is 8072.5047, HALF_UP to 8073.
        assertEquals(8_073L, totalCostMinorOf(45_123L, 1_789L, eurFactor))
    }

    @Test
    fun totalCostIsExactWhenTheProductIsExact() {
        assertEquals(6_000L, totalCostMinorOf(40_000L, 1_500L, eurFactor))
    }

    @Test
    fun totalCostRoundsHalfUpAtTheExactHalfwayPoint() {
        // 1 L at 0.005 EUR/L is exactly 0.5 minor units; HALF_UP takes it to 1.
        assertEquals(1L, totalCostMinorOf(1_000L, 5L, eurFactor))
    }

    @Test
    fun totalCostRoundsDownBelowHalfAMinorUnit() {
        // 0.001 L at 0.001 EUR/L is 0.0001 minor units, which HALF_UP takes to 0.
        assertEquals(0L, totalCostMinorOf(1L, 1L, eurFactor))
    }

    @Test
    fun totalCostDoesNotOverflowOnTheLargestDocumentedInputs() {
        // The intermediate product is 49_999_950_000_000, six orders of magnitude past Int.
        assertEquals(49_999_950L, totalCostMinorOf(500_000L, 999_999L, eurFactor))
    }

    @Test
    fun pricePerLiterInvertsTheGoldenTotals() {
        assertEquals(1_500L, pricePerLiterScaledOf(6_000L, 40_000L, eurFactor))
        assertEquals(999_999L, pricePerLiterScaledOf(49_999_950L, 500_000L, eurFactor))
    }

    @Test
    fun litersInvertsTheGoldenTotals() {
        assertEquals(40_000L, litersScaledOf(6_000L, 1_500L, eurFactor))
        assertEquals(500_000L, litersScaledOf(49_999_950L, 999_999L, eurFactor))
    }
}
