package com.ruizurraca.carapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AnonymousReminderBannerTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun theNoticeIsShownWithItsOwnBodyAndCanBeDismissed() {
        var dismissals = 0
        composeRule.setContent {
            AnonymousReminderBanner(index = 3, onDismiss = { dismissals += 1 })
        }

        composeRule.onNodeWithTag(AnonymousReminderTestTags.BANNER).assertIsDisplayed()
        composeRule.onNodeWithText(reminderBody(R.string.anonymous_reminder_body_4)).assertIsDisplayed()
        composeRule.onNodeWithTag(AnonymousReminderTestTags.DISMISS).performClick()

        assertEquals(1, dismissals)
    }

    @Test
    fun theFirstNoticeStatesTheThirtyDayCleanupRiskToo() {
        composeRule.setContent {
            AnonymousReminderBanner(index = 0, onDismiss = {})
        }

        composeRule.onNodeWithText(reminderBody(R.string.anonymous_reminder_body_1)).assertIsDisplayed()
    }

    @Test
    fun everyReminderStatesTheRecoveryBenefitAndTheThirtyDayRisk() {
        (0..3).forEach { index ->
            val body = reminderBody(anonymousReminderBodyResource(index))

            assertTrue("Reminder $index hides the cleanup deadline.", body.contains("30 days"))
            assertTrue("Reminder $index hides the recovery benefit.", body.contains("Sign in with"))
        }
    }

    private fun reminderBody(resource: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resource)
}
