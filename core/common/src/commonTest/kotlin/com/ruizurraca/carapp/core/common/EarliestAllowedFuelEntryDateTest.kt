package com.ruizurraca.carapp.core.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class EarliestAllowedFuelEntryDateTest {
    @Test
    fun subtractsTwentyCalendarYearsInUtcAndPreservesTimeOfDay() {
        assertEquals(
            Instant.parse("2006-08-29T12:34:56.789Z"),
            earliestAllowedFuelEntryDate(Instant.parse("2026-08-29T12:34:56.789Z")),
        )
    }

    @Test
    fun libraryClampsSyntheticLeapDayWhenTargetCenturyIsNotLeap() {
        assertEquals(
            Instant.parse("2100-02-28T23:59:59.123456789Z"),
            earliestAllowedFuelEntryDate(Instant.parse("2120-02-29T23:59:59.123456789Z")),
        )
    }

    @Test
    fun producerClampsDatesBeforeUnixEpoch() {
        assertEquals(
            Instant.fromEpochMilliseconds(0L),
            earliestAllowedFuelEntryDate(Instant.parse("1980-06-15T08:00:00Z")),
        )
    }
}
