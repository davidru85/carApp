package com.ruizurraca.carapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
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

        composeRule.onNodeWithTag(VehicleTestTags.ADD_VEHICLE).performClick()
        composeRule.onNodeWithTag(VehicleTestTags.FUEL_TYPE_INPUT).assertDoesNotExist()
        composeRule.onNodeWithTag(VehicleTestTags.NAME).performTextInput(vehicleName)
        composeRule.onNodeWithTag(VehicleTestTags.ODOMETER).performTextReplacement("125")
        composeRule.onNodeWithTag(VehicleTestTags.SAVE).performClick()

        composeRule
            .onNodeWithTag(VehicleTestTags.DETAIL_NAME)
            .assertIsDisplayed()
            .assertTextEquals(vehicleName)
        composeRule.onNodeWithTag(VehicleTestTags.FIRST_FUEL_INVITATION).assertIsDisplayed()
    }
}
