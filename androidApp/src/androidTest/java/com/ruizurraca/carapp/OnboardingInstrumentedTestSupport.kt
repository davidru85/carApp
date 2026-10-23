package com.ruizurraca.carapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.rules.ActivityScenarioRule

internal fun AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>.openVehicleCreation() {
    waitUntil(timeoutMillis = 30_000) {
        onAllNodesWithTag(OnboardingTestTags.GUEST).fetchSemanticsNodes().isNotEmpty() ||
            onAllNodesWithTag(VehicleTestTags.ADD_VEHICLE).fetchSemanticsNodes().isNotEmpty() ||
            onAllNodesWithTag(VehicleTestTags.NAME).fetchSemanticsNodes().isNotEmpty()
    }
    if (onAllNodesWithTag(OnboardingTestTags.GUEST).fetchSemanticsNodes().isNotEmpty()) {
        onNodeWithTag(OnboardingTestTags.GUEST).performClick()
    }
    waitUntil(timeoutMillis = 30_000) {
        onAllNodesWithTag(VehicleTestTags.ADD_VEHICLE).fetchSemanticsNodes().isNotEmpty() ||
            onAllNodesWithTag(VehicleTestTags.NAME).fetchSemanticsNodes().isNotEmpty()
    }
    if (onAllNodesWithTag(VehicleTestTags.NAME).fetchSemanticsNodes().isNotEmpty()) {
        onNodeWithTag(VehicleTestTags.NAME).performTextInput("Test setup vehicle ${System.nanoTime()}")
        onNodeWithTag(VehicleTestTags.SAVE).performClick()
        // F-1 routes a mandatory first-vehicle save to the vehicle detail, not back to the list, so
        // the resolution here accepts either surface before it leaves the detail.
        waitUntil(timeoutMillis = 30_000) {
            onAllNodesWithTag(VehicleTestTags.ADD_VEHICLE).fetchSemanticsNodes().isNotEmpty() ||
                onAllNodesWithTag(VehicleTestTags.DETAIL_NAME).fetchSemanticsNodes().isNotEmpty()
        }
        if (onAllNodesWithTag(VehicleTestTags.ADD_VEHICLE).fetchSemanticsNodes().isEmpty()) {
            activityRule.scenario.onActivity { activity -> activity.onBackPressedDispatcher.onBackPressed() }
            waitUntil(timeoutMillis = 30_000) {
                onAllNodesWithTag(VehicleTestTags.ADD_VEHICLE).fetchSemanticsNodes().isNotEmpty()
            }
        }
    }
    onNodeWithTag(VehicleTestTags.ADD_VEHICLE).performClick()
    waitUntil(timeoutMillis = 5_000) {
        onAllNodesWithTag(VehicleTestTags.NAME).fetchSemanticsNodes().isNotEmpty()
    }
    onNodeWithTag(VehicleTestTags.NAME).assertIsDisplayed()
}
