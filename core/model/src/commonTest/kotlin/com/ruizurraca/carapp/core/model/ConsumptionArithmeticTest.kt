package com.ruizurraca.carapp.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

/** Golden values of `docs/CONTRACTS.md §2`. Every row of that table MUST be covered. */
class ConsumptionArithmeticTest {
    @Test
    fun segmentRoundsDown() {
        // Exact value is 7.5205 L/100 km.
        assertEquals(752L, segmentConsumptionScaledOf(45_123L, 600L))
    }

    @Test
    fun segmentIsExact() {
        assertEquals(800L, segmentConsumptionScaledOf(40_000L, 500L))
    }

    @Test
    fun segmentRoundsUp() {
        // Exact value is 7.55668 L/100 km.
        assertEquals(756L, segmentConsumptionScaledOf(30_000L, 397L))
    }

    @Test
    fun averageIsDistanceWeightedAndNotTheMeanOfTheSegments() {
        // Segments (45_123 L, 600 km) and (40_000 L, 500 km) combined.
        val average = averageConsumptionScaledOf(85_123L, 1_100L)

        assertEquals(774L, average, "Exact value is 7.73845 L/100 km")

        val arithmeticMeanOfSegments = (
            segmentConsumptionScaledOf(45_123L, 600L) + segmentConsumptionScaledOf(40_000L, 500L)
            ) / 2L
        assertEquals(776L, arithmeticMeanOfSegments)
        assertEquals(
            false,
            average == arithmeticMeanOfSegments,
            "The average MUST NOT be recomputed from the rounded segment values",
        )
    }
}
