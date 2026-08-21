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

    /**
     * **Known contract defect, awaiting an owner decision.**
     *
     * `docs/CONTRACTS.md §2` golden row 3 expects `1` for these inputs and annotates it
     * "`1` (0.01 €) — rounds up from 0.0001". The formula in the same section, which the section
     * also says MUST be implemented literally, yields `0`:
     *
     * ```text
     * (1 * 1 * 100 + 500_000) / 1_000_000 = 500_100 / 1_000_000 = 0
     * ```
     *
     * The formula is right and the expectation is wrong. 0.001 L at 0.001 €/L is 0.000001 €,
     * which is 0.0001 minor units; HALF_UP of 0.0001 is 0, not 1. The row's parenthetical
     * "(0.01 €)" is one cent, ten thousand times the real value.
     *
     * This test asserts the literal formula, because "implement the formula literally" is an
     * unambiguous MUST while the golden row contradicts both the formula and HALF_UP. It is
     * recorded for the owner as decision `DEC-1` in `docs/handoff-E0-03.md`; if the owner chooses
     * a minimum-one-minor-unit rule instead, this test and the formula change together.
     */
    @Test
    fun smallestNonZeroInputsRoundToZeroUnderTheLiteralFormula() {
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
