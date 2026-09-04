package com.ruizurraca.carapp

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ruizurraca.carapp.core.common.AuthProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class OnboardingFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun androidWelcomeHasExactlyGoogleAndGuestActionsWithNamedIntents() {
        var selectedProvider: AuthProvider? = null
        var anonymousStarts = 0
        composeRule.setContent {
            WelcomeScreen(
                state =
                    SessionUiState(
                        phase = SessionPhase.SIGNED_OUT,
                        providers = emptyList(),
                        isBusy = false,
                        message = null,
                    ),
                onGoogle = { selectedProvider = AuthProvider.GOOGLE },
                onContinueWithoutAccount = { anonymousStarts += 1 },
            )
        }

        composeRule.onAllNodesWithTag(OnboardingTestTags.ACTION).assertCountEquals(2)
        composeRule.onNodeWithTag(OnboardingTestTags.GOOGLE).assertIsDisplayed().performClick()
        assertEquals(AuthProvider.GOOGLE, selectedProvider)
        composeRule.onNodeWithTag(OnboardingTestTags.GUEST).assertIsDisplayed().performClick()
        assertEquals(1, anonymousStarts)
        composeRule.onNodeWithTag(OnboardingTestTags.APPLE).assertDoesNotExist()
    }
}
