package com.ruizurraca.carapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** `E4-02`: at a 200% device font scale the vehicle list keeps its add action and the Settings entry. */
@RunWith(AndroidJUnit4::class)
class VehicleListSystemFontScaleTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @After
    fun restoreSystemFontScale() {
        setSystemFontScale(1f)
    }

    @Test
    fun vehicleListActionsStayDisplayedAtDoubleSystemFontScale() {
        setSystemFontScale(DOUBLE_FONT_SCALE)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            reachVehicleList(scenario)
            composeRule.onNodeWithTag(VehicleTestTags.ADD_VEHICLE).assertIsDisplayed()
            composeRule.onNodeWithTag(SETTINGS_ENTRY_TAG).assertIsDisplayed()
        }
    }

    private fun reachVehicleList(scenario: ActivityScenario<MainActivity>) {
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.hasNodeWithTag(OnboardingTestTags.GUEST) ||
                composeRule.hasNodeWithTag(VehicleTestTags.ADD_VEHICLE) ||
                composeRule.hasNodeWithTag(VehicleTestTags.NAME)
        }
        if (composeRule.hasNodeWithTag(OnboardingTestTags.GUEST)) {
            composeRule.onNodeWithTag(OnboardingTestTags.GUEST).performClick()
        }
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.hasNodeWithTag(VehicleTestTags.ADD_VEHICLE) || composeRule.hasNodeWithTag(VehicleTestTags.NAME)
        }
        if (!composeRule.hasNodeWithTag(VehicleTestTags.ADD_VEHICLE)) {
            composeRule.onNodeWithTag(VehicleTestTags.NAME).performTextInput("Font scale vehicle ${System.nanoTime()}")
            composeRule.onNodeWithTag(VehicleTestTags.SAVE).performScrollTo().performClick()
            composeRule.waitUntil(timeoutMillis = 30_000) { composeRule.hasNodeWithTag(VehicleTestTags.DETAIL_NAME) }
            scenario.onActivity { activity -> activity.onBackPressedDispatcher.onBackPressed() }
        }
        composeRule.waitUntil(timeoutMillis = 30_000) { composeRule.hasNodeWithTag(VehicleTestTags.ADD_VEHICLE) }
    }
}
