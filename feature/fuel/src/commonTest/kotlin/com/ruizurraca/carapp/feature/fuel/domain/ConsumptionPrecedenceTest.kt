package com.ruizurraca.carapp.feature.fuel.domain

import com.ruizurraca.carapp.core.model.ConsumptionInvalidReason
import com.ruizurraca.carapp.core.model.SegmentResult
import kotlin.test.Test
import kotlin.test.assertEquals

class ConsumptionPrecedenceTest {
    private val calculate = DefaultCalculateConsumption()

    @Test
    fun noPreviousFullTankPrecedesMissedEntries() {
        assertOnlyReason(
            entries =
                listOf(
                    consumptionEntry(
                        idNumber = 1,
                        odometerKm = 1_000L,
                        hasMissedEntries = true,
                    ),
                ),
            reason = ConsumptionInvalidReason.NoPreviousFullTank,
        )
    }

    @Test
    fun noPreviousFullTankPrecedesInconsistentOdometer() {
        assertOnlyReason(
            entries =
                listOf(
                    consumptionEntry(
                        idNumber = 1,
                        odometerKm = 1_000L,
                        odometerInconsistent = true,
                    ),
                ),
            reason = ConsumptionInvalidReason.NoPreviousFullTank,
        )
    }

    @Test
    fun nonPositiveDistancePrecedesIntermediateDuplicateOdometer() {
        assertLastReason(
            entries =
                listOf(
                    consumptionEntry(idNumber = 1, odometerKm = 1_000L),
                    consumptionEntry(idNumber = 2, odometerKm = 1_000L, isFullTank = false),
                    consumptionEntry(idNumber = 3, odometerKm = 1_000L),
                ),
            reason = ConsumptionInvalidReason.NonPositiveDistance,
        )
    }

    @Test
    fun nonPositiveDistancePrecedesMissedEntries() {
        assertLastReason(
            entries =
                listOf(
                    consumptionEntry(idNumber = 1, odometerKm = 1_000L),
                    consumptionEntry(
                        idNumber = 2,
                        odometerKm = 1_000L,
                        hasMissedEntries = true,
                    ),
                ),
            reason = ConsumptionInvalidReason.NonPositiveDistance,
        )
    }

    @Test
    fun nonPositiveDistancePrecedesInconsistentOdometer() {
        assertLastReason(
            entries =
                listOf(
                    consumptionEntry(idNumber = 1, odometerKm = 1_000L),
                    consumptionEntry(
                        idNumber = 2,
                        odometerKm = 1_000L,
                        odometerInconsistent = true,
                    ),
                ),
            reason = ConsumptionInvalidReason.NonPositiveDistance,
        )
    }

    @Test
    fun zeroDistanceNeedsNoConcurrentFlag() {
        assertLastReason(
            entries =
                listOf(
                    consumptionEntry(idNumber = 1, odometerKm = 1_000L),
                    consumptionEntry(idNumber = 2, odometerKm = 1_000L),
                ),
            reason = ConsumptionInvalidReason.NonPositiveDistance,
        )
    }

    @Test
    fun intermediateDuplicateOdometerPrecedesMissedEntries() {
        assertLastReason(
            entries =
                listOf(
                    consumptionEntry(idNumber = 1, odometerKm = 1_000L),
                    consumptionEntry(
                        idNumber = 2,
                        odometerKm = 1_000L,
                        isFullTank = false,
                        hasMissedEntries = true,
                    ),
                    consumptionEntry(idNumber = 3, odometerKm = 1_500L),
                ),
            reason = ConsumptionInvalidReason.DuplicateOdometerInSegment,
        )
    }

    @Test
    fun intermediateDuplicateOdometerPrecedesInconsistentOdometer() {
        assertLastReason(
            entries =
                listOf(
                    consumptionEntry(idNumber = 1, odometerKm = 1_000L),
                    consumptionEntry(
                        idNumber = 2,
                        odometerKm = 1_000L,
                        isFullTank = false,
                        odometerInconsistent = true,
                    ),
                    consumptionEntry(idNumber = 3, odometerKm = 1_500L),
                ),
            reason = ConsumptionInvalidReason.DuplicateOdometerInSegment,
        )
    }

    @Test
    fun missedEntriesPrecedeInconsistentOdometer() {
        assertLastReason(
            entries =
                listOf(
                    consumptionEntry(idNumber = 1, odometerKm = 1_000L),
                    consumptionEntry(
                        idNumber = 2,
                        odometerKm = 1_200L,
                        isFullTank = false,
                        hasMissedEntries = true,
                        odometerInconsistent = true,
                    ),
                    consumptionEntry(idNumber = 3, odometerKm = 1_500L),
                ),
            reason = ConsumptionInvalidReason.MissedEntriesInSegment,
        )
    }

    private fun assertOnlyReason(
        entries: List<com.ruizurraca.carapp.core.model.FuelEntry>,
        reason: ConsumptionInvalidReason,
    ) {
        val result = calculate(entries).segments.single() as SegmentResult.Invalid
        assertEquals(reason, result.reason)
    }

    private fun assertLastReason(
        entries: List<com.ruizurraca.carapp.core.model.FuelEntry>,
        reason: ConsumptionInvalidReason,
    ) {
        val result = calculate(entries).segments.last() as SegmentResult.Invalid
        assertEquals(reason, result.reason)
    }
}
