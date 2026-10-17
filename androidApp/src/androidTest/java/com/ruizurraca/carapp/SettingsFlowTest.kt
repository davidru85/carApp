package com.ruizurraca.carapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `E4-01` through the real app: Settings is reachable from the vehicle list, analytics is off on a
 * fresh install, a selected currency is used by a new refuel, and deleting local data returns to first
 * run with default settings. The graph is reset before and after every test (`D-172`).
 */
@RunWith(AndroidJUnit4::class)
class SettingsFlowTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Before
    fun startFromAFreshInstall() {
        resetApplicationState()
    }

    @After
    fun leaveNoStateBehind() {
        resetApplicationState()
    }

    @Test
    fun settingsAreReachableFromTheVehicleList() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            composeRule.reachTheVehicleList(scenario, uniqueVehicleName("Settings entry"))

            composeRule.openSettings()

            composeRule.onNodeWithTag(SettingsTestTags.SCREEN).assertIsDisplayed()
        }
    }

    @Test
    fun analyticsIsOffOnAFreshInstall() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            composeRule.reachTheVehicleList(scenario, uniqueVehicleName("Analytics default"))
            composeRule.openSettings()
            composeRule.waitForLoadedSettings()

            composeRule.onNodeWithTag(SettingsTestTags.ANALYTICS_SWITCH).performScrollTo().assertIsOff()
        }
    }

    @Test
    fun aSelectedCurrencyIsUsedByANewFuelEntry() {
        val currency = nonDefaultCurrency()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            composeRule.reachTheVehicleList(scenario, uniqueVehicleName("Currency setup"))
            composeRule.openSettings()
            composeRule.selectCurrency(currency)
            composeRule.onNodeWithTag(SettingsTestTags.BACK).performClick()
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.hasNodeWithTag(VehicleTestTags.ADD_VEHICLE)
            }
            composeRule.createVehicleFromTheList(uniqueVehicleName("Currency"))
            composeRule.onNodeWithTag(FuelEntryTestTags.ADD_FUEL_ENTRY).performClick()
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.hasNodeWithTag(FuelEntryTestTags.LITERS)
            }
            composeRule.onNodeWithTag(FuelEntryTestTags.LITERS).performTextReplacement("40")
            composeRule.onNodeWithTag(FuelEntryTestTags.PRICE_PER_LITER).performTextReplacement("1.5")

            val derivedInCurrency = hasTestTag(FuelEntryTestTags.DERIVED_VALUE) and hasText(currency, substring = true)
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodes(derivedInCurrency).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule
                .onNodeWithTag(FuelEntryTestTags.DERIVED_VALUE)
                .assertTextContains(currency, substring = true)
        }
    }

    @Test
    fun deletingLocalDataReturnsToFirstRunWithDefaultSettings() {
        val defaultCurrency = deviceDefaultCurrency()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            composeRule.reachTheVehicleList(scenario, uniqueVehicleName("Before deletion"))
            composeRule.openSettings()
            composeRule.selectCurrency(nonDefaultCurrency())

            composeRule.deleteLocalDataWithItsConfirmations()

            composeRule.onNodeWithTag(OnboardingTestTags.GUEST).assertIsDisplayed().performClick()
            composeRule.waitUntil(timeoutMillis = 30_000) { composeRule.hasNodeWithTag(VehicleTestTags.NAME) }
            // F-1: an owner without vehicles is routed to mandatory creation, which offers no way back.
            composeRule.onNodeWithTag(VehicleTestTags.BACK).assertDoesNotExist()
            composeRule.reachTheVehicleList(scenario, uniqueVehicleName("After deletion"))
            composeRule.openSettings()
            composeRule.waitForLoadedSettings()
            composeRule
                .onNodeWithTag(SettingsTestTags.CURRENCY_VALUE, useUnmergedTree = true)
                .assertTextContains("($defaultCurrency)", substring = true)
            composeRule.onNodeWithTag(SettingsTestTags.ANALYTICS_SWITCH).performScrollTo().assertIsOff()
        }
    }
}
