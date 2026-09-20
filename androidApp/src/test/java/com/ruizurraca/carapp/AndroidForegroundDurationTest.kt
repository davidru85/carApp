package com.ruizurraca.carapp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The Android background-duration measurement behind `docs/CONTRACTS.md §9.8`. The clock is injected
 * so the arithmetic is tested on the host instead of only observed on a device.
 */
class AndroidForegroundDurationTest {
    @Test
    fun aColdStartReportsNoBackgroundDuration() {
        val duration = AndroidForegroundDuration { 1_000 }

        assertNull(
            duration.onForegrounded(),
            "the first foreground entry has no preceding background stay",
        )
    }

    @Test
    fun aForegroundReturnReportsTheElapsedBackgroundTime() {
        var now = 1_000L
        val duration = AndroidForegroundDuration { now }

        duration.onBackgrounded()
        now = 6_000

        assertEquals(5_000L, duration.onForegrounded())
    }

    @Test
    fun theMeasurementIsNotReusedByTheNextForegroundEntry() {
        var now = 1_000L
        val duration = AndroidForegroundDuration { now }

        duration.onBackgrounded()
        now = 6_000
        assertEquals(5_000L, duration.onForegrounded())

        now = 7_000
        assertNull(duration.onForegrounded())
    }

    @Test
    fun aRepeatedBackgroundEntryKeepsTheEarliestMoment() {
        var now = 1_000L
        val duration = AndroidForegroundDuration { now }

        duration.onBackgrounded()
        now = 2_000
        duration.onBackgrounded()
        now = 6_000

        assertEquals(5_000L, duration.onForegrounded())
    }
}
