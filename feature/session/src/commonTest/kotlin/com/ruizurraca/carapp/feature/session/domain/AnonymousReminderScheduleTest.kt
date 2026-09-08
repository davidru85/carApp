package com.ruizurraca.carapp.feature.session.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * The `D-62` schedule of `docs/CONTRACTS.md §11.3`. Every boundary named there is normative, and
 * the anchor is always the Firebase anonymous user-creation timestamp, never the previous display.
 */
class AnonymousReminderScheduleTest {
    @Test
    fun theScheduleIsTheFixedElapsedDayThresholdListOfDecision62() {
        assertEquals(listOf(1, 3, 8, 18), ANONYMOUS_REMINDER_ELAPSED_DAYS)
    }

    @Test
    fun noReminderIsDueTwelveHoursAfterAccountCreation() {
        assertNull(dueIndexAfter(12.hours, lastShownIndex = null))
    }

    @Test
    fun theFirstReminderIsDueOnDayOne() {
        assertEquals(0, dueIndexAfter(1.days, lastShownIndex = null))
    }

    @Test
    fun noNewReminderIsDueOnDayTwoBecauseTheFirstOneWasAlreadyShown() {
        assertNull(dueIndexAfter(2.days, lastShownIndex = 0))
    }

    @Test
    fun theSecondReminderIsDueOnDayFour() {
        assertEquals(1, dueIndexAfter(4.days, lastShownIndex = 0))
    }

    @Test
    fun theThirdReminderIsDueOnDayNine() {
        assertEquals(2, dueIndexAfter(9.days, lastShownIndex = 1))
    }

    @Test
    fun dayTwentyEmitsOnlyTheLastReminderAndConsumesEveryEarlierOne() {
        assertEquals(
            3,
            dueIndexAfter(20.days, lastShownIndex = null),
            "A return after an inactive period shows the highest due reminder once, not a backlog.",
        )
    }

    @Test
    fun noReminderIsDueOnDayThirtyOneOnceTheScheduleIsComplete() {
        assertNull(dueIndexAfter(31.days, lastShownIndex = 3))
    }

    @Test
    fun aClockBehindTheAccountCreationTimestampEmitsNothing() {
        assertNull(
            dueAnonymousReminderIndex(
                accountCreatedAt = CREATED_AT,
                now = CREATED_AT - 1.days,
                lastShownIndex = null,
            ),
        )
    }

    private fun dueIndexAfter(
        elapsed: kotlin.time.Duration,
        lastShownIndex: Int?,
    ): Int? =
        dueAnonymousReminderIndex(
            accountCreatedAt = CREATED_AT,
            now = CREATED_AT + elapsed,
            lastShownIndex = lastShownIndex,
        )

    private companion object {
        /** 2026-01-01T00:00:00Z, so no expectation depends on the wall clock. */
        val CREATED_AT: Instant = Instant.fromEpochMilliseconds(1_767_225_600_000L)
    }
}
