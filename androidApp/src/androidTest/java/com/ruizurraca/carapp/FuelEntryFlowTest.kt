package com.ruizurraca.carapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class FuelEntryFlowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun confirmedPartialRefuelRendersExplanationAndBothIndicators() {
        val vehicleName = "Fuel test vehicle ${System.currentTimeMillis()}"

        composeRule.onNodeWithTag(VehicleTestTags.ADD_VEHICLE).performClick()
        composeRule.onNodeWithTag(VehicleTestTags.NAME).performTextInput(vehicleName)
        composeRule.onNodeWithTag(VehicleTestTags.ODOMETER).performTextReplacement("100")
        composeRule.onNodeWithTag(VehicleTestTags.SAVE).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(VehicleTestTags.DETAIL_NAME).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(FuelEntryTestTags.CONSUMPTION_EMPTY).assertIsDisplayed()

        composeRule.onNodeWithTag(FuelEntryTestTags.ADD_FUEL_ENTRY).performClick()
        composeRule.onNodeWithTag(FuelEntryTestTags.ODOMETER).performTextReplacement("50")
        composeRule.onNodeWithTag(FuelEntryTestTags.LITERS).performTextReplacement("40")
        composeRule.onNodeWithTag(FuelEntryTestTags.PRICE_PER_LITER).performTextReplacement("1.5")
        composeRule
            .onNodeWithTag(FuelEntryTestTags.DERIVED_VALUE)
            .assertTextContains("60.00", substring = true)
        composeRule.onNodeWithTag(FuelEntryTestTags.FULL_TANK).performScrollTo().performClick()
        composeRule.onNodeWithTag(FuelEntryTestTags.MISSED_ENTRIES).performScrollTo().performClick()
        composeRule.onNodeWithTag(FuelEntryTestTags.SAVE).performClick()

        composeRule.onNodeWithTag(FuelEntryTestTags.ODOMETER_WARNING).assertIsDisplayed()
        composeRule.onNodeWithTag(FuelEntryTestTags.CONFIRM_ODOMETER_WARNING).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("No consumption: partial tank.").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("No consumption: partial tank.").assertIsDisplayed()
        composeRule.onNodeWithText("Partial tank").assertIsDisplayed()
        composeRule.onNodeWithText("Missed refuels").assertIsDisplayed()
        composeRule.onNodeWithText("Inconsistent odometer").assertIsDisplayed()
    }

    @Test
    fun selectedCalendarDayUsesTheInjectedDeviceZone() {
        val calendarDay = FuelEntryCalendarDay(ZoneId.of("Europe/Madrid"), Locale.UK)
        val earlyMorningLocalInstant = Instant.parse("2026-08-30T22:30:00Z")

        assertEquals(LocalDate.of(2026, 8, 31), calendarDay.localDate(earlyMorningLocalInstant.toEpochMilli()))
        assertEquals(
            Instant.parse("2026-10-24T22:00:00Z").toEpochMilli(),
            calendarDay.atStartOfDay(year = 2026, zeroBasedMonth = 9, dayOfMonth = 25),
        )
    }
}
