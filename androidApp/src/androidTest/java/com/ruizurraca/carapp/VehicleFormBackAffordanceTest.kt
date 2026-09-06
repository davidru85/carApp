package com.ruizurraca.carapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class VehicleFormBackAffordanceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun firstRunVehicleFormOffersNoBackAffordance() {
        composeRule.setContent {
            VehicleFormTopBar(titleResource = R.string.create_vehicle_title, onBack = null)
        }

        composeRule.onNodeWithTag(VehicleTestTags.BACK).assertDoesNotExist()
    }

    @Test
    fun vehicleFormOpenedOverAnotherScreenOffersABackAffordance() {
        var backRequests = 0
        composeRule.setContent {
            VehicleFormTopBar(
                titleResource = R.string.create_vehicle_title,
                onBack = { backRequests += 1 },
            )
        }

        composeRule.onNodeWithTag(VehicleTestTags.BACK).assertIsDisplayed().performClick()

        assertEquals(1, backRequests)
    }
}
