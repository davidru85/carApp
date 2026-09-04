package com.ruizurraca.carapp

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FirstVehicleOnboardingTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun firstVehicleCreationOffersNoBackAffordanceAndRoutesToTheCreatedVehicleDetail() {
        val vehicleName = "First run vehicle ${System.currentTimeMillis()}"
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(DATABASE_FILE_NAME)

        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.waitUntil(timeoutMillis = 30_000) {
                composeRule.onAllNodesWithTag(OnboardingTestTags.GUEST).fetchSemanticsNodes().isNotEmpty() ||
                    composeRule.onAllNodesWithTag(VehicleTestTags.NAME).fetchSemanticsNodes().isNotEmpty()
            }
            if (composeRule.onAllNodesWithTag(OnboardingTestTags.GUEST).fetchSemanticsNodes().isNotEmpty()) {
                composeRule.onNodeWithTag(OnboardingTestTags.GUEST).performClick()
            }
            composeRule.waitUntil(timeoutMillis = 30_000) {
                composeRule.onAllNodesWithTag(VehicleTestTags.NAME).fetchSemanticsNodes().isNotEmpty()
            }

            composeRule.onNodeWithTag(VehicleTestTags.BACK).assertDoesNotExist()

            composeRule.onNodeWithTag(VehicleTestTags.NAME).performTextInput(vehicleName)
            composeRule.onNodeWithTag(VehicleTestTags.ODOMETER).performTextReplacement("410")
            composeRule.onNodeWithTag(VehicleTestTags.SAVE).performClick()

            composeRule.waitUntil(timeoutMillis = 30_000) {
                composeRule.onAllNodesWithTag(VehicleTestTags.DETAIL_NAME).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag(VehicleTestTags.DETAIL_NAME).assertTextContains(vehicleName)
            composeRule.onNodeWithTag(VehicleTestTags.FIRST_FUEL_INVITATION).assertExists()
        }
    }
}
