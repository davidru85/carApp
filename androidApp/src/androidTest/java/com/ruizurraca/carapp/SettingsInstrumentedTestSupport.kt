package com.ruizurraca.carapp

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import kotlinx.coroutines.runBlocking

/**
 * Closes the process-scoped graph and only then deletes its database (`D-172`), exactly as
 * `FirstVehicleOnboardingTest` does. The full-app Settings tests call it before launching the Activity
 * and again after closing it, so a destructive flow leaves no state behind for the next test.
 */
internal fun resetApplicationState() {
    runBlocking { AndroidAppGraph.resetForTests() }
}

internal fun uniqueVehicleName(prefix: String): String = "$prefix ${System.nanoTime()}"

/** The currency the settings defaults derive from on this device (`SqlDelightSettingsRepository`). */
internal fun deviceDefaultCurrency(): String = AndroidLocaleProvider().current().suggestedCurrency.value

/** A supported currency that is not this device's default, so a selection is observable. */
internal fun nonDefaultCurrency(): String = if (deviceDefaultCurrency() == "CHF") "SEK" else "CHF"

internal fun ComposeTestRule.hasNodeWithTag(tag: String): Boolean =
    onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

/**
 * Drives a launched app to its vehicle list. The welcome screen appears only for a signed-out owner,
 * and an empty list routes to mandatory first-vehicle creation (F-1), whose save lands on the vehicle
 * detail; a list restored from a backup is already there.
 */
internal fun ComposeTestRule.reachTheVehicleList(
    scenario: ActivityScenario<MainActivity>,
    firstVehicleName: String,
) {
    waitUntil(timeoutMillis = LONG_WAIT_MILLIS) {
        hasNodeWithTag(OnboardingTestTags.GUEST) || isOnAResolvedOwnerSurface()
    }
    if (hasNodeWithTag(OnboardingTestTags.GUEST)) {
        onNodeWithTag(OnboardingTestTags.GUEST).performClick()
        waitUntil(timeoutMillis = LONG_WAIT_MILLIS) { isOnAResolvedOwnerSurface() }
    }
    if (hasNodeWithTag(VehicleTestTags.NAME)) {
        saveTheOpenVehicleForm(firstVehicleName)
        scenario.onActivity { activity -> activity.onBackPressedDispatcher.onBackPressed() }
        waitUntil(timeoutMillis = SHORT_WAIT_MILLIS) {
            hasNodeWithTag(VehicleTestTags.ADD_VEHICLE) && !hasNodeWithTag(VehicleTestTags.DETAIL_NAME)
        }
    }
}

/** From the vehicle list: creates a vehicle and stays on its detail, where a refuel can be added. */
internal fun ComposeTestRule.createVehicleFromTheList(vehicleName: String) {
    onNodeWithTag(VehicleTestTags.ADD_VEHICLE).performClick()
    waitUntil(timeoutMillis = SHORT_WAIT_MILLIS) { hasNodeWithTag(VehicleTestTags.NAME) }
    saveTheOpenVehicleForm(vehicleName)
}

internal fun ComposeTestRule.openSettings() {
    onNodeWithTag(SettingsTestTags.ENTRY).performClick()
    waitUntil(timeoutMillis = SHORT_WAIT_MILLIS) { hasNodeWithTag(SettingsTestTags.SCREEN) }
}

/** The currency row becomes actionable only once the first settings row has been read. */
internal fun ComposeTestRule.waitForLoadedSettings() {
    val actionableCurrency = hasTestTag(SettingsTestTags.CURRENCY_ROW) and hasClickAction()
    waitUntil(timeoutMillis = SHORT_WAIT_MILLIS) {
        onAllNodes(actionableCurrency).fetchSemanticsNodes().isNotEmpty()
    }
}

internal fun ComposeTestRule.selectCurrency(code: String) {
    waitForLoadedSettings()
    onNodeWithTag(SettingsTestTags.CURRENCY_ROW).performClick()
    onNodeWithTag(SettingsTestTags.currencyOption(code)).performScrollTo().performClick()
    val selected = hasTestTag(SettingsTestTags.CURRENCY_VALUE) and hasText("($code)", substring = true)
    waitUntil(timeoutMillis = SHORT_WAIT_MILLIS) {
        onAllNodes(selected, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }
}

/**
 * Requests the local-data deletion and answers its confirmations. An owner whose rows have not reached
 * the backup is warned first and discards them (`docs/CONTRACTS.md §20.10`); every owner then confirms
 * the destructive `DeleteLocalData` step. Ends on the welcome screen.
 */
internal fun ComposeTestRule.deleteLocalDataWithItsConfirmations() {
    onNodeWithTag(SettingsTestTags.DELETE_LOCAL_DATA_ROW).performScrollTo().performClick()
    waitUntil(timeoutMillis = SHORT_WAIT_MILLIS) {
        hasNodeWithTag(SettingsTestTags.PENDING_SYNC_DIALOG) ||
            hasNodeWithTag(SettingsTestTags.CONFIRM_DELETE_LOCAL_DATA_DIALOG)
    }
    if (hasNodeWithTag(SettingsTestTags.PENDING_SYNC_DIALOG)) {
        onNodeWithTag(SettingsTestTags.PENDING_SYNC_DISCARD).performClick()
        waitUntil(timeoutMillis = SHORT_WAIT_MILLIS) {
            hasNodeWithTag(SettingsTestTags.CONFIRM_DELETE_LOCAL_DATA_DIALOG)
        }
    }
    onNodeWithTag(SettingsTestTags.CONFIRM_DESTRUCTIVE).performClick()
    waitUntil(timeoutMillis = LONG_WAIT_MILLIS) { hasNodeWithTag(OnboardingTestTags.GUEST) }
}

private fun ComposeTestRule.saveTheOpenVehicleForm(vehicleName: String) {
    onNodeWithTag(VehicleTestTags.NAME).performTextInput(vehicleName)
    onNodeWithTag(VehicleTestTags.SAVE).performClick()
    waitUntil(timeoutMillis = LONG_WAIT_MILLIS) { hasNodeWithTag(VehicleTestTags.DETAIL_NAME) }
}

/**
 * The vehicle list is known: either F-1 routed an empty list to the first-vehicle form, or the list
 * shows rows. `ADD_VEHICLE` alone proves nothing, because the list is composed under the waiting
 * overlay while it is still loading.
 */
private fun ComposeTestRule.isOnAResolvedOwnerSurface(): Boolean =
    hasNodeWithTag(VehicleTestTags.NAME) || onAllNodes(hasAVehicleRow()).fetchSemanticsNodes().isNotEmpty()

private fun hasAVehicleRow(): SemanticsMatcher =
    SemanticsMatcher("has a vehicle row test tag") { node ->
        node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(VEHICLE_ROW_TAG_PREFIX) == true
    }

private val VEHICLE_ROW_TAG_PREFIX = VehicleTestTags.vehicleRow(vehicleId = "")
private const val LONG_WAIT_MILLIS = 30_000L
private const val SHORT_WAIT_MILLIS = 10_000L
