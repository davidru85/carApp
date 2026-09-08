package com.ruizurraca.carapp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class AnonymousReminderCopyTest {
    @Test
    fun everyScheduledReminderHasItsOwnBody() {
        val bodies = (0..3).map(::anonymousReminderBodyResource)

        assertEquals(
            bodies.size,
            bodies.distinct().size,
            "Repeating the same sentence four times reads as a bug, not as an escalating warning.",
        )
    }

    @Test
    fun theLastReminderDoesNotReuseTheFirstOneWording() {
        assertNotEquals(anonymousReminderBodyResource(0), anonymousReminderBodyResource(3))
    }

    @Test
    fun anIndexOutsideTheScheduleFallsBackToTheMostUrgentBody() {
        assertEquals(anonymousReminderBodyResource(3), anonymousReminderBodyResource(9))
        assertEquals(anonymousReminderBodyResource(0), anonymousReminderBodyResource(-1))
    }
}
