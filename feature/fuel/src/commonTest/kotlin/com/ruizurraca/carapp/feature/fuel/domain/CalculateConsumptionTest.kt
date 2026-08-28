package com.ruizurraca.carapp.feature.fuel.domain

import com.ruizurraca.carapp.core.model.ConsumptionInvalidReason
import com.ruizurraca.carapp.core.model.ConsumptionL100Km
import com.ruizurraca.carapp.core.model.SegmentResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class CalculateConsumptionTest {
    private val calculate = DefaultCalculateConsumption()

    @Test
    fun twoFullTanksCreateAnAnchorAndOneValidSegment() {
        val anchor = consumptionEntry(idNumber = 1, odometerKm = 1_000L)
        val end = consumptionEntry(idNumber = 2, odometerKm = 1_500L)

        val report = calculate(listOf(end, anchor))

        assertEquals(
            SegmentResult.Invalid(anchor.id, ConsumptionInvalidReason.NoPreviousFullTank),
            report.segments[0],
        )
        assertEquals(
            SegmentResult.Valid(
                fromEntryId = anchor.id,
                toEntryId = end.id,
                litersScaled = 40_000L,
                distanceKm = 500L,
                consumption = ConsumptionL100Km(800L),
            ),
            report.segments[1],
        )
        assertEquals(1, report.validSegmentCount)
        assertEquals(ConsumptionL100Km(800L), report.average)
        assertFalse(report.isReliable)
    }

    @Test
    fun partialEntryContributesWithoutCreatingItsOwnSegment() {
        val anchor = consumptionEntry(idNumber = 1, odometerKm = 1_000L)
        val partial =
            consumptionEntry(
                idNumber = 2,
                odometerKm = 1_200L,
                litersScaled = 10_000L,
                isFullTank = false,
            )
        val end = consumptionEntry(idNumber = 3, odometerKm = 1_500L, litersScaled = 30_000L)

        val report = calculate(listOf(partial, end, anchor))

        assertEquals(2, report.segments.size)
        val segment = assertIs<SegmentResult.Valid>(report.segments[1])
        assertEquals(40_000L, segment.litersScaled)
        assertEquals(ConsumptionL100Km(800L), segment.consumption)
        assertTrue(
            report.segments
                .filterIsInstance<SegmentResult.Invalid>()
                .none { it.reason == ConsumptionInvalidReason.EndEntryNotFullTank },
        )
    }

    @Test
    fun missedPartialEntryInvalidatesOnlyItsContainingSegment() {
        val first = consumptionEntry(idNumber = 1, odometerKm = 0L)
        val second = consumptionEntry(idNumber = 2, odometerKm = 500L)
        val missedPartial =
            consumptionEntry(
                idNumber = 3,
                odometerKm = 750L,
                isFullTank = false,
                hasMissedEntries = true,
            )
        val third = consumptionEntry(idNumber = 4, odometerKm = 1_000L)

        val report = calculate(listOf(third, first, missedPartial, second))

        assertIs<SegmentResult.Valid>(report.segments[1])
        assertEquals(
            SegmentResult.Invalid(third.id, ConsumptionInvalidReason.MissedEntriesInSegment),
            report.segments[2],
        )
    }

    @Test
    fun missedFlagOnAFullEndDoesNotInvalidateTheFollowingSegment() {
        val first = consumptionEntry(idNumber = 1, odometerKm = 0L)
        val flaggedEnd =
            consumptionEntry(
                idNumber = 2,
                odometerKm = 500L,
                hasMissedEntries = true,
            )
        val next = consumptionEntry(idNumber = 3, odometerKm = 1_000L)

        val report = calculate(listOf(first, flaggedEnd, next))

        assertEquals(
            SegmentResult.Invalid(flaggedEnd.id, ConsumptionInvalidReason.MissedEntriesInSegment),
            report.segments[1],
        )
        assertIs<SegmentResult.Valid>(report.segments[2])
    }

    @Test
    fun inconsistentIntermediateEntryInvalidatesItsContainingSegment() {
        val anchor = consumptionEntry(idNumber = 1, odometerKm = 1_000L)
        val inconsistent =
            consumptionEntry(
                idNumber = 2,
                odometerKm = 1_200L,
                isFullTank = false,
                odometerInconsistent = true,
            )
        val end = consumptionEntry(idNumber = 3, odometerKm = 1_500L)

        val report = calculate(listOf(anchor, inconsistent, end))

        assertEquals(
            SegmentResult.Invalid(end.id, ConsumptionInvalidReason.InconsistentOdometerInSegment),
            report.segments[1],
        )
    }

    @Test
    fun backDatedPartialEntryStillUsesOdometerFirstCalculationOrder() {
        val anchor = consumptionEntry(idNumber = 1, dateMillis = 30_000L, odometerKm = 1_000L)
        val backDatedPartial =
            consumptionEntry(
                idNumber = 2,
                dateMillis = 10_000L,
                odometerKm = 1_200L,
                litersScaled = 10_000L,
                isFullTank = false,
            )
        val end =
            consumptionEntry(
                idNumber = 3,
                dateMillis = 20_000L,
                odometerKm = 1_500L,
                litersScaled = 30_000L,
            )

        val segment =
            assertIs<SegmentResult.Valid>(
                calculate(listOf(end, backDatedPartial, anchor)).segments[1],
            )

        assertEquals(40_000L, segment.litersScaled)
        assertEquals(500L, segment.distanceKm)
    }

    @Test
    fun calculationOrderUsesOdometerThenDateThenId() {
        val highOdometer = consumptionEntry(idNumber = 4, dateMillis = 1_000L, odometerKm = 2_000L)
        val laterDate = consumptionEntry(idNumber = 3, dateMillis = 3_000L, odometerKm = 1_000L)
        val higherId = consumptionEntry(idNumber = 2, dateMillis = 2_000L, odometerKm = 1_000L)
        val lowerId = consumptionEntry(idNumber = 1, dateMillis = 2_000L, odometerKm = 1_000L)

        val report = calculate(listOf(highOdometer, laterDate, higherId, lowerId))

        assertEquals(
            listOf(lowerId.id, higherId.id, laterDate.id, highOdometer.id),
            report.segments.map {
                when (it) {
                    is SegmentResult.Invalid -> it.toEntryId
                    is SegmentResult.Valid -> it.toEntryId
                }
            },
        )
    }

    @Test
    fun directlySuppliedEntriesAreNotFilteredByVehicleOrDeletion() {
        val anchor = consumptionEntry(idNumber = 1, odometerKm = 1_000L)
        val foreignDeletedPartial =
            consumptionEntry(
                idNumber = 2,
                vehicleId = otherVehicleId,
                odometerKm = 1_200L,
                litersScaled = 10_000L,
                isFullTank = false,
                deletedAt = Instant.fromEpochMilliseconds(5_000L),
            )
        val end = consumptionEntry(idNumber = 3, odometerKm = 1_500L, litersScaled = 30_000L)

        val segment =
            assertIs<SegmentResult.Valid>(
                calculate(listOf(anchor, foreignDeletedPartial, end)).segments[1],
            )

        assertEquals(40_000L, segment.litersScaled)
        assertEquals(ConsumptionL100Km(800L), segment.consumption)
    }

    @Test
    fun functionIsTotalForEmptyPartialAndExtremeInputs() {
        val empty = calculate(emptyList())
        val partialOnly =
            calculate(
                listOf(
                    consumptionEntry(
                        idNumber = 1,
                        odometerKm = Long.MIN_VALUE,
                        litersScaled = Long.MAX_VALUE,
                        isFullTank = false,
                    ),
                ),
            )
        val extremeFullEntries =
            calculate(
                listOf(
                    consumptionEntry(idNumber = 2, odometerKm = Long.MIN_VALUE),
                    consumptionEntry(idNumber = 3, odometerKm = Long.MAX_VALUE),
                ),
            )

        assertEquals(0, empty.validSegmentCount)
        assertEquals(0, partialOnly.validSegmentCount)
        val extremeReason =
            assertIs<SegmentResult.Invalid>(extremeFullEntries.segments.last()).reason
        assertNotEquals(ConsumptionInvalidReason.EndEntryNotFullTank, extremeReason)
    }
}
