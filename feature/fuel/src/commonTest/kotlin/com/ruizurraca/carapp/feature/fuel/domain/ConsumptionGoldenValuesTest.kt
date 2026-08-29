package com.ruizurraca.carapp.feature.fuel.domain

import com.ruizurraca.carapp.core.model.ConsumptionL100Km
import com.ruizurraca.carapp.core.model.SegmentResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ConsumptionGoldenValuesTest {
    private val calculate = DefaultCalculateConsumption()

    @Test
    fun segmentGoldenValuesCoverDownExactAndUpRounding() {
        listOf(
            GoldenSegment(litersScaled = 45_123L, distanceKm = 600L, expected = 752L),
            GoldenSegment(litersScaled = 40_000L, distanceKm = 500L, expected = 800L),
            GoldenSegment(litersScaled = 30_000L, distanceKm = 397L, expected = 756L),
        ).forEachIndexed { index, golden ->
            val anchor = consumptionEntry(idNumber = index * 2 + 1, odometerKm = 0L)
            val end =
                consumptionEntry(
                    idNumber = index * 2 + 2,
                    odometerKm = golden.distanceKm,
                    litersScaled = golden.litersScaled,
                )

            val segment = assertIs<SegmentResult.Valid>(calculate(listOf(end, anchor)).segments[1])

            assertEquals(ConsumptionL100Km(golden.expected), segment.consumption)
        }
    }

    @Test
    fun averageIsDistanceWeightedFromUnroundedTotals() {
        val first = consumptionEntry(idNumber = 1, odometerKm = 0L)
        val second = consumptionEntry(idNumber = 2, odometerKm = 600L, litersScaled = 45_123L)
        val third = consumptionEntry(idNumber = 3, odometerKm = 1_100L, litersScaled = 40_000L)

        val report = calculate(listOf(third, first, second))

        assertEquals(2, report.validSegmentCount)
        assertEquals(ConsumptionL100Km(774L), report.average)
        assertTrue(report.isReliable)
        val valid = report.segments.filterIsInstance<SegmentResult.Valid>()
        val arithmeticMean = valid.sumOf { it.consumption.scaled } / valid.size
        assertEquals(776L, arithmeticMean)
        assertFalse(report.average?.scaled == arithmeticMean)
    }

    private data class GoldenSegment(
        val litersScaled: Long,
        val distanceKm: Long,
        val expected: Long,
    )
}
