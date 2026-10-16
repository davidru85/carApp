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

    /**
     * Every reminder states the recovery benefit and the thirty-day risk in both shipped languages.
     * Each catalogue is read directly, so the result never depends on the device language (`E4-02`).
     */
    @Test
    fun everyReminderStatesTheRecoveryBenefitAndTheThirtyDayRisk() {
        val requiredPhrases =
            mapOf(
                "en-US" to listOf("30 days", "Sign in with"),
                "es-ES" to listOf("30 días", "Inicia sesión con"),
            )
        requiredPhrases.forEach { (languageTag, phrases) ->
            (0..3).forEach { index ->
                val body = localizedString(languageTag, anonymousReminderBodyResource(index))
                phrases.forEach { phrase ->
                    assertTrue("Reminder $index in $languageTag lacks \"$phrase\".", body.contains(phrase))
                }
            }
        }
    }

    private fun reminderBody(resource: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resource)
}
