package com.ruizurraca.carapp.feature.fuel.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConsumptionSegmentFactsTest {
    @Test
    fun intermediateEntrySharingTheAnchorOdometerCountsItsLitersAndMarksTheDuplicate() {
        val anchor = consumptionEntry(idNumber = 1, odometerKm = 1_000L)
        val duplicatePartial =
            consumptionEntry(
                idNumber = 2,
                odometerKm = 1_000L,
                litersScaled = 5_000L,
                isFullTank = false,
            )
        val end = consumptionEntry(idNumber = 3, odometerKm = 1_500L, litersScaled = 35_000L)

        val facts = consumptionSegmentFacts(listOf(anchor, duplicatePartial, end), anchor, end)

        assertEquals(40_000L, facts.litersScaled)
        assertTrue(facts.hasDuplicateStartOdometer)
    }
}
