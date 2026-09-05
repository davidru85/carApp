package com.ruizurraca.carapp

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
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
    fun moneyFieldsPreserveIncrementalEditingAndLiveDerivationAcrossRecreation() {
        val vehicleName = "Money typing vehicle ${System.currentTimeMillis()}"

        composeRule.openVehicleCreation()
        composeRule.onNodeWithTag(VehicleTestTags.NAME).performTextInput(vehicleName)
        composeRule.onNodeWithTag(VehicleTestTags.ODOMETER).performTextReplacement("100")
        composeRule.onNodeWithTag(VehicleTestTags.SAVE).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(FuelEntryTestTags.ADD_FUEL_ENTRY).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(FuelEntryTestTags.ADD_FUEL_ENTRY).performClick()

        val liters = composeRule.onNodeWithTag(FuelEntryTestTags.LITERS)
        liters.performTextInput("4")
        liters.performTextInput("0")
        liters.performTextInput(",")
        liters.performTextInput("0")
        liters.performTextInput("0")
        liters.performTextInput("1")
        liters.assertTextContains("40,001")
        liters.performKeyInput { pressKey(Key.Backspace) }
        liters.assertTextContains("40,00")
        liters.performTextInput("0")
        liters.assertTextContains("40,000")

        val price = composeRule.onNodeWithTag(FuelEntryTestTags.PRICE_PER_LITER)
        price.performTextInput("1")
        price.performTextInput(".")
        price.performTextInput("5")
        price.performTextInput("4")
        price.performKeyInput { pressKey(Key.Backspace) }
        price.assertTextContains("1.5")
        price.performTextInput("4")
        price.performTextInput("9")
        price.assertTextContains("1.549")
        price.performTextInput("9")
        price.assertTextContains("1.549")

        composeRule.onNodeWithTag(FuelEntryTestTags.DERIVED_VALUE).assertTextContains("61.96", substring = true)

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(FuelEntryTestTags.LITERS).assertTextContains("40,000")
        composeRule.onNodeWithTag(FuelEntryTestTags.PRICE_PER_LITER).assertTextContains("1.549")
        composeRule.onNodeWithTag(FuelEntryTestTags.DERIVED_VALUE).assertTextContains("61.96", substring = true)
    }

    @Test
    fun confirmedPartialRefuelRendersExplanationAndBothIndicators() {
        val vehicleName = "Fuel test vehicle ${System.currentTimeMillis()}"

        composeRule.openVehicleCreation()
        composeRule.onNodeWithTag(VehicleTestTags.NAME).performTextInput(vehicleName)
        composeRule.onNodeWithTag(VehicleTestTags.ODOMETER).performTextReplacement("100")
        composeRule.onNodeWithTag(VehicleTestTags.SAVE).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(VehicleTestTags.DETAIL_NAME).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(FuelEntryTestTags.CONSUMPTION_EMPTY).fetchSemanticsNodes().isNotEmpty()
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

    @Test
    fun odometerFieldCanBeClearedAndRetypedWithoutLosingFocus() {
        val vehicleName = "Odometer vehicle ${System.currentTimeMillis()}"

        composeRule.openVehicleCreation()
        composeRule.onNodeWithTag(VehicleTestTags.NAME).performTextInput(vehicleName)
        composeRule.onNodeWithTag(VehicleTestTags.ODOMETER).performTextReplacement("100")
        composeRule.onNodeWithTag(VehicleTestTags.SAVE).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(FuelEntryTestTags.ADD_FUEL_ENTRY).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(FuelEntryTestTags.ADD_FUEL_ENTRY).performClick()

        val odometer = composeRule.onNodeWithTag(FuelEntryTestTags.ODOMETER)
        odometer.performTextReplacement("50")
        odometer.performTextReplacement("")
        odometer.assertTextContains("")
        odometer.performTextInput("1")
        odometer.assertTextContains("1")
        odometer.performTextInput("2")
        odometer.performTextInput("3")
        odometer.assertTextContains("123")
    }
}
