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
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.AnnotatedString
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ruizurraca.carapp.feature.vehicle.domain.INITIAL_ODOMETER_RANGE_KM
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VehicleCreationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun createsVehicleAndRoutesToEmptyDetailWithTheDefaultFuelType() {
        val vehicleName = "Instrumented vehicle ${System.currentTimeMillis()}"

        composeRule.openVehicleCreation()
        // `D-127` exposes the selector over the five MVP values, defaulting to GASOLINE.
        composeRule
            .onNodeWithTag(VehicleTestTags.FUEL_TYPE_INPUT)
            .assertIsDisplayed()
            .assertTextContains(DEFAULT_FUEL_TYPE_LABEL)
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
    fun fuelTypeSelectorOffersExactlyTheFiveMvpValues() {
        composeRule.openVehicleCreation()

        composeRule.onNodeWithTag(VehicleTestTags.FUEL_TYPE_INPUT).performClick()

        // `D-127` offers exactly the five MVP values. Asserting the localized label of each one and
        // the total option count together catches both a missing value and an extra one, which is
        // how an `ELECTRIC`/`HYBRID` leak would appear.
        MVP_FUEL_TYPE_LABELS.forEach { label ->
            assertTrue(
                "$label must be offered",
                composeRule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty(),
            )
        }
        assertEquals(
            5,
            composeRule.onAllNodesWithTag(VehicleTestTags.FUEL_TYPE_OPTION).fetchSemanticsNodes().size,
        )
    }

    @Test
    fun selectedFuelTypePersistsOnCreationAndOnEdit() {
        val vehicleName = "Fuel type vehicle ${System.currentTimeMillis()}"

        composeRule.openVehicleCreation()
        composeRule.onNodeWithTag(VehicleTestTags.NAME).performTextInput(vehicleName)
        composeRule.onNodeWithTag(VehicleTestTags.ODOMETER).performTextReplacement("125")
        composeRule.onNodeWithTag(VehicleTestTags.FUEL_TYPE_INPUT).performClick()
        composeRule.onNodeWithText("Diesel").performClick()
        composeRule
            .onNodeWithTag(VehicleTestTags.FUEL_TYPE_INPUT)
            .assertTextContains("Diesel")
        composeRule.onNodeWithTag(VehicleTestTags.SAVE).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(VehicleTestTags.DETAIL_NAME).fetchSemanticsNodes().isNotEmpty()
        }

        // Edit mode loads the persisted value rather than the creation default.
        composeRule.onNodeWithText("Edit vehicle").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(VehicleTestTags.FUEL_TYPE_INPUT).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule
            .onNodeWithTag(VehicleTestTags.FUEL_TYPE_INPUT)
            .assertTextContains("Diesel")

        // An updated selection is persisted by the next save. Saving an edit does not navigate
        // away on Android (the form stays open), so the test returns with the back affordance and
        // reopens the form to read the persisted value back.
        composeRule.onNodeWithTag(VehicleTestTags.FUEL_TYPE_INPUT).performClick()
        composeRule.onNodeWithText("LPG").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(VehicleTestTags.FUEL_TYPE_OPTION).fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag(VehicleTestTags.SAVE).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(VehicleTestTags.BACK).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(VehicleTestTags.DETAIL_NAME).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Edit vehicle").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(VehicleTestTags.FUEL_TYPE_INPUT).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule
            .onNodeWithTag(VehicleTestTags.FUEL_TYPE_INPUT)
            .assertTextContains("LPG")
    }

    @Test
    fun configurationChangePreservesTheDraftAndBackStackExitReleasesIt() {
        val draftName = "Rotating draft ${System.currentTimeMillis()}"

        composeRule.openVehicleCreation()
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
        composeRule.openVehicleCreation()

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

        composeRule.openVehicleCreation()
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

        composeRule.openVehicleCreation()

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
}

/** The five MVP `FuelType` display labels of the English catalogue (`D-127`). */
private val MVP_FUEL_TYPE_LABELS = listOf("Petrol", "Diesel", "LPG", "CNG", "Other")

/** `D-127` keeps `GASOLINE` as the default for a new vehicle. */
private const val DEFAULT_FUEL_TYPE_LABEL = "Petrol"
