package com.ruizurraca.carapp

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.AnnotatedString
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ruizurraca.carapp.feature.vehicle.domain.INITIAL_ODOMETER_RANGE_KM
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VehicleCreationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun createsVehicleAndRoutesToEmptyDetailWithoutFuelTypeInput() {
        val vehicleName = "Instrumented vehicle ${System.currentTimeMillis()}"

        openVehicleCreation()
        composeRule.onNodeWithTag(VehicleTestTags.FUEL_TYPE_INPUT).assertDoesNotExist()
        composeRule.onNodeWithTag(VehicleTestTags.NAME).performTextInput(vehicleName)
        composeRule.onNodeWithTag(VehicleTestTags.NAME).assertTextContains(vehicleName)
        composeRule.onNodeWithTag(VehicleTestTags.ODOMETER).performTextReplacement("125")
        composeRule.onNodeWithTag(VehicleTestTags.NAME).assertTextContains(vehicleName)
        composeRule.onNodeWithTag(VehicleTestTags.SAVE).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(VehicleTestTags.DETAIL_NAME).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(VehicleTestTags.FIRST_FUEL_INVITATION).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule
            .onNodeWithTag(VehicleTestTags.DETAIL_NAME)
            .assertIsDisplayed()
            .assertTextEquals(vehicleName)
        composeRule.onNodeWithTag(VehicleTestTags.FIRST_FUEL_INVITATION).assertIsDisplayed()
    }

    @Test
    fun configurationChangePreservesTheDraftAndBackStackExitReleasesIt() {
        val draftName = "Rotating draft ${System.currentTimeMillis()}"

        openVehicleCreation()
        composeRule.onNodeWithTag(VehicleTestTags.NAME).performTextInput(draftName)
        composeRule.onNodeWithTag(VehicleTestTags.ODOMETER).performTextReplacement("321")
        composeRule.waitForIdle()

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(VehicleTestTags.NAME).assertTextContains(draftName)
        composeRule.onNodeWithTag(VehicleTestTags.ODOMETER).assertTextContains("321")

        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
        openVehicleCreation()

        composeRule
            .onNodeWithTag(VehicleTestTags.NAME)
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.EditableText,
                    AnnotatedString(""),
                ),
            )
        composeRule.onNodeWithTag(VehicleTestTags.ODOMETER).assertTextContains("0")
    }

    @Test
    fun odometerPreservesInvalidRawInputAndShowsLocalizedError() {
        val overflowingOdometer = "999999999999999999999"

        openVehicleCreation()
        composeRule.onNodeWithTag(VehicleTestTags.ODOMETER).performTextReplacement("")
        composeRule
            .onNodeWithTag(VehicleTestTags.ODOMETER)
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.EditableText,
                    AnnotatedString(""),
                ),
            )
        composeRule
            .onNodeWithTag(VehicleTestTags.ERROR)
            .assertIsDisplayed()
            .assertTextEquals("Enter a value within the allowed range.")

        composeRule.onNodeWithTag(VehicleTestTags.ODOMETER).performTextReplacement(overflowingOdometer)
        composeRule
            .onNodeWithTag(VehicleTestTags.ODOMETER)
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.EditableText,
                    AnnotatedString(overflowingOdometer),
                ),
            )
        composeRule
            .onNodeWithTag(VehicleTestTags.ERROR)
            .assertIsDisplayed()
            .assertTextEquals("Enter a value within the allowed range.")
    }

    @Test
    fun odometerValidationUsesTheSharedDomainRange() {
        val hostFields = Class.forName("com.ruizurraca.carapp.MainActivityKt").declaredFields
        assertFalse(hostFields.any { field -> field.name == "ODOMETER_RANGE_KM" })

        openVehicleCreation()

        composeRule
            .onNodeWithTag(VehicleTestTags.ODOMETER)
            .performTextReplacement(INITIAL_ODOMETER_RANGE_KM.first.toString())
        composeRule.onNodeWithTag(VehicleTestTags.ERROR).assertDoesNotExist()
        composeRule.onNodeWithTag(VehicleTestTags.SAVE).assertIsEnabled()

        composeRule
            .onNodeWithTag(VehicleTestTags.ODOMETER)
            .performTextReplacement((INITIAL_ODOMETER_RANGE_KM.first - 1).toString())
        composeRule.onNodeWithTag(VehicleTestTags.ERROR).assertIsDisplayed()
        composeRule.onNodeWithTag(VehicleTestTags.SAVE).assertIsNotEnabled()

        composeRule
            .onNodeWithTag(VehicleTestTags.ODOMETER)
            .performTextReplacement(INITIAL_ODOMETER_RANGE_KM.last.toString())
        composeRule.onNodeWithTag(VehicleTestTags.ERROR).assertDoesNotExist()
        composeRule.onNodeWithTag(VehicleTestTags.SAVE).assertIsEnabled()

        composeRule
            .onNodeWithTag(VehicleTestTags.ODOMETER)
            .performTextReplacement((INITIAL_ODOMETER_RANGE_KM.last + 1).toString())
        composeRule.onNodeWithTag(VehicleTestTags.ERROR).assertIsDisplayed()
        composeRule.onNodeWithTag(VehicleTestTags.SAVE).assertIsNotEnabled()
    }

    private fun openVehicleCreation() {
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithTag(OnboardingTestTags.GUEST).fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithTag(VehicleTestTags.ADD_VEHICLE).fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithTag(VehicleTestTags.NAME).fetchSemanticsNodes().isNotEmpty()
        }
        if (composeRule.onAllNodesWithTag(OnboardingTestTags.GUEST).fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithTag(OnboardingTestTags.GUEST).performClick()
        }
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithTag(VehicleTestTags.ADD_VEHICLE).fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithTag(VehicleTestTags.NAME).fetchSemanticsNodes().isNotEmpty()
        }
        if (composeRule.onAllNodesWithTag(VehicleTestTags.NAME).fetchSemanticsNodes().isNotEmpty()) {
            composeRule
                .onNodeWithTag(VehicleTestTags.NAME)
                .performTextInput("Test setup vehicle ${System.nanoTime()}")
            composeRule.onNodeWithTag(VehicleTestTags.SAVE).performClick()
            composeRule.waitUntil(timeoutMillis = 30_000) {
                composeRule.onAllNodesWithTag(VehicleTestTags.ADD_VEHICLE).fetchSemanticsNodes().isNotEmpty()
            }
        }
        composeRule.onNodeWithTag(VehicleTestTags.ADD_VEHICLE).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(VehicleTestTags.NAME).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(VehicleTestTags.NAME).assertIsDisplayed()
    }
}
