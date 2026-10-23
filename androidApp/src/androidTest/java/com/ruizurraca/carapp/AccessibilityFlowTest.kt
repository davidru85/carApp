package com.ruizurraca.carapp

import android.os.Build
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `E4-02` (`D-216`): ATF runs before every action of the real F-1 to F-3 flows and on each screen
 * they reach, so a screen with an accessibility ERROR fails the flow that shows it.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class AccessibilityFlowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun enableChecks() {
        composeRule.enableAccessibilityChecks(carAppAccessibilityValidator())
    }

    @Test
    fun vehicleFormPassesAccessibilityChecks() {
        composeRule.openVehicleCreation()
        composeRule.checkAccessibilityOfEveryWindow()
    }

    @Test
    fun vehicleListPassesAccessibilityChecks() {
        openVehicleList()
        composeRule.checkAccessibilityOfEveryWindow()
    }

    @Test
    fun vehicleDetailPassesAccessibilityChecks() {
        createVehicleAndOpenDetail()
        composeRule.checkAccessibilityOfEveryWindow()
    }

    @Test
    fun vehicleDeleteConfirmationPassesAccessibilityChecks() {
        createVehicleAndOpenDetail()
        composeRule.onNodeWithTag(VehicleTestTags.DELETE).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.hasNodeWithTag(VehicleTestTags.CONFIRM_DELETE_VEHICLE)
        }
        composeRule.checkAccessibilityOfEveryWindow()
        composeRule.onNodeWithTag(VehicleTestTags.CANCEL_DELETE_VEHICLE).performClick()
    }

    @Test
    fun fuelEntryFormPassesAccessibilityChecks() {
        createVehicleAndOpenDetail()
        composeRule.onNodeWithTag(FuelEntryTestTags.ADD_FUEL_ENTRY).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { composeRule.hasNodeWithTag(FuelEntryTestTags.LITERS) }
        composeRule.checkAccessibilityOfEveryWindow()
    }

    @Test
    fun odometerWarningPassesAccessibilityChecks() {
        createVehicleAndOpenDetail()
        fillLowerOdometerRefuel()
        composeRule.onNodeWithTag(FuelEntryTestTags.SAVE).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { composeRule.hasNodeWithTag(FuelEntryTestTags.ODOMETER_WARNING) }
        composeRule.checkAccessibilityOfEveryWindow()
        composeRule.onNodeWithTag(FuelEntryTestTags.CANCEL_ODOMETER_WARNING).performClick()
    }

    @Test
    fun fuelEntryListWithIndicatorsPassesAccessibilityChecks() {
        createVehicleAndOpenDetail()
        fillLowerOdometerRefuel()
        composeRule.onNodeWithTag(FuelEntryTestTags.FULL_TANK).performScrollTo().performClick()
        composeRule.onNodeWithTag(FuelEntryTestTags.MISSED_ENTRIES).performScrollTo().performClick()
        composeRule.onNodeWithTag(FuelEntryTestTags.SAVE).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.hasNodeWithTag(FuelEntryTestTags.CONFIRM_ODOMETER_WARNING)
        }
        composeRule.onNodeWithTag(FuelEntryTestTags.CONFIRM_ODOMETER_WARNING).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(targetString(R.string.partial_tank)).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.checkAccessibilityOfEveryWindow()
    }

    @Test
    fun settingsPassesAccessibilityChecks() {
        openVehicleList()
        composeRule.onNodeWithTag(SETTINGS_ENTRY_TAG).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasTestTagPrefix("settings_")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.checkAccessibilityOfEveryWindow()
    }

    private fun openVehicleList() {
        composeRule.openVehicleCreation()
        composeRule.onNodeWithTag(VehicleTestTags.BACK).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { composeRule.hasNodeWithTag(VehicleTestTags.ADD_VEHICLE) }
    }

    private fun createVehicleAndOpenDetail() {
        composeRule.openVehicleCreation()
        composeRule.onNodeWithTag(VehicleTestTags.NAME).performTextInput("A11y vehicle ${System.currentTimeMillis()}")
        composeRule.onNodeWithTag(VehicleTestTags.ODOMETER).performTextReplacement("100")
        composeRule.onNodeWithTag(VehicleTestTags.SAVE).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { composeRule.hasNodeWithTag(VehicleTestTags.DETAIL_NAME) }
    }

    /** Odometer 50 is below the vehicle's 100, so saving triggers the two-step warning. */
    private fun fillLowerOdometerRefuel() {
        composeRule.onNodeWithTag(FuelEntryTestTags.ADD_FUEL_ENTRY).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { composeRule.hasNodeWithTag(FuelEntryTestTags.LITERS) }
        composeRule.onNodeWithTag(FuelEntryTestTags.ODOMETER).performTextReplacement("50")
        composeRule.onNodeWithTag(FuelEntryTestTags.LITERS).performTextReplacement("40")
        composeRule.onNodeWithTag(FuelEntryTestTags.PRICE_PER_LITER).performTextReplacement("1.5")
    }
}
