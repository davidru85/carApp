package com.ruizurraca.carapp

import android.os.ParcelFileDescriptor
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.Locales
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.intl.LocaleList
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `E4-02`: each screen renders its Spanish copy and Spanish number formatting under an `es-ES`
 * configuration. Expected text is read from the Spanish resources and formatted with the product
 * functions; a literal check proves the decimal comma or the thousands point really appears.
 */
@RunWith(AndroidJUnit4::class)
class SpanishLocaleSmokeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun welcomeRendersSpanishCopy() {
        showInSpanish {
            WelcomeScreen(state = a11ySession(SessionPhase.SIGNED_OUT), onGoogle = {}, onContinueWithoutAccount = {})
        }
        val spanish = localizedString(SPANISH_TAG, R.string.continue_with_google)
        assertNotEquals(localizedString("en-US", R.string.continue_with_google), spanish)
        composeRule.onNodeWithText(spanish).assertIsDisplayed()
    }

    @Test
    fun vehicleFormRendersSpanishCopy() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val holder = newA11yVehicleFormHolder(scope)
        try {
            showInSpanish {
                VehicleFormScreen(stateHolder = holder, originalVehicleId = null, onBack = {}, onSaved = {})
            }
            composeRule.onNodeWithText(localizedString(SPANISH_TAG, R.string.create_vehicle_title)).assertIsDisplayed()
            composeRule.scrollToAndAssertDisplayed(hasText(localizedString(SPANISH_TAG, R.string.save_vehicle)))
        } finally {
            holder.close()
            scope.cancel()
        }
    }

    @Test
    fun vehicleDetailFormatsNumbersForSpain() {
        showInSpanish {
            VehicleDetailLayout(
                vehicle = A11Y_VEHICLE,
                isLoading = false,
                fuelState = a11yFuelState(),
                onBack = {},
                onAddFuelEntry = {},
                onEdit = {},
                onDelete = {},
                onEditEntry = {},
            )
        }
        val odometer = localizedString(SPANISH_TAG, R.string.vehicle_odometer, formatInteger(A11Y_ODOMETER_KM, SPANISH))
        val consumption =
            localizedString(SPANISH_TAG, R.string.consumption_value, formatScaledDecimal(724L, 2, SPANISH))
        assertTrue(odometer, odometer.contains("142.500"))
        assertTrue(consumption, consumption.contains("7,24"))
        composeRule.onNodeWithText(odometer).assertIsDisplayed()
        composeRule.onNodeWithTag(FuelEntryTestTags.CONSUMPTION_AVERAGE).assertTextEquals(consumption)
    }

    @Test
    fun fuelEntryFormFormatsTheDerivedTotalForSpain() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val holder = newA11yFuelEntryFormHolder(scope)
        try {
            showInSpanish { FuelEntryFormScreen(stateHolder = holder, onBack = {}, onSaved = {}) }
            val title = localizedString(SPANISH_TAG, R.string.create_fuel_entry_title)
            composeRule.onNodeWithText(title).assertIsDisplayed()
            composeRule.onNodeWithTag(FuelEntryTestTags.LITERS).performTextInput("40")
            composeRule.onNodeWithTag(FuelEntryTestTags.PRICE_PER_LITER).performTextInput("2")
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule
                    .onAllNodes(hasTestTag(FuelEntryTestTags.DERIVED_VALUE) and hasText("80,00", substring = true))
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
        } finally {
            holder.close()
            scope.cancel()
        }
    }

    @Test
    fun settingsRendersSpanishCopy() {
        showInSpanish {
            SettingsScreen(
                settings = a11ySettings(),
                session = a11ySession(SessionPhase.LOCAL),
                sync = a11ySync(),
                appVersion = A11Y_APP_VERSION,
                callbacks = a11ySettingsCallbacks(),
            )
        }
        val idleLabel = localizedString(SPANISH_TAG, syncStatusLabelResource(SyncStatusVisual.IDLE))
        composeRule.scrollToAndAssertDisplayed(hasTextContaining(idleLabel))
    }

    @Test
    fun departureRendersSpanishCopy() {
        showInSpanish { A11yDepartureScreen(failedDepartureSession()) }
        val error = localizedString(SPANISH_TAG, R.string.error_persistence)
        composeRule.scrollToAndAssertDisplayed(hasTextContaining(error))
    }

    @Test
    fun conversionConfirmationRendersSpanishCopy() {
        // A Material3 dialog opens its own window and takes no context from
        // `DeviceConfigurationOverride.Locales`, so this screen is rendered under the app locale the
        // §3 V-6 evidence capture uses (`cmd locale set-app-locales`).
        val session = conversionCollisionSession()
        withTheSpanishAppLocale {
            showInSpanish {
                SettingsScreen(
                    settings = a11ySettings(),
                    session = session,
                    sync = a11ySync(),
                    appVersion = A11Y_APP_VERSION,
                    callbacks = a11ySettingsCallbacks(),
                )
                // The collision is presented by the authenticated root, beside Settings (`R2`).
                SettingsSessionPrompts(session = session, callbacks = a11yPromptCallbacks())
            }
            composeRule.waitForIdle()
            val copy = localizedString(SPANISH_TAG, R.string.confirm_replace_existing_account)
            assertTrue(copy, copy.contains("sustituye permanentemente"))
            composeRule.scrollToAndAssertDisplayed(hasTextContaining(copy))
            listOf(SettingsTestTags.CONVERSION_CONFIRM, SettingsTestTags.CONVERSION_CANCEL)
                .forEach(composeRule::scrollToAndAssertDisplayed)
        }
    }

    /**
     * The §3 V-6 mechanism: the app's own locale is set through the shell and read back, so the
     * dialog window of the next composition resolves the Spanish catalogue. It is cleared again so the
     * following tests keep the emulator's en-US default.
     */
    private fun withTheSpanishAppLocale(action: () -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val packageName = instrumentation.targetContext.packageName
        runShellCommand("cmd locale set-app-locales $packageName --locales $SPANISH_TAG")
        try {
            action()
        } finally {
            runShellCommand("cmd locale set-app-locales $packageName --locales \"\"")
        }
    }

    private fun runShellCommand(command: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ParcelFileDescriptor
            .AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
            .use { stream -> stream.readBytes() }
    }

    private fun showInSpanish(content: @Composable () -> Unit) {
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.Locales(LocaleList(SPANISH_TAG))) {
                CarAppTestTheme(content)
            }
        }
    }

    @Test
    fun reauthenticationRendersSpanishCopy() {
        showInSpanish { A11yDepartureScreen(reauthenticationSession(), true) }
        composeRule
            .onNodeWithText(localizedString(SPANISH_TAG, R.string.continue_with_google))
            .assertIsDisplayed()
        composeRule.onNodeWithText(localizedString(SPANISH_TAG, R.string.cancel)).assertIsDisplayed()
        composeRule
            .onNodeWithText(localizedString(SPANISH_TAG, R.string.error_auth_requires_recent_login))
            .assertIsDisplayed()
    }

    @Test
    fun aRetainedReauthenticationFailureRendersSpanishCopy() {
        showInSpanish { A11yDepartureScreen(reauthenticationSession("AUTH.NETWORK_UNAVAILABLE"), true) }
        composeRule
            .onNodeWithText(localizedString(SPANISH_TAG, R.string.error_auth_network))
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(localizedString(SPANISH_TAG, R.string.continue_with_google))
            .assertIsDisplayed()
        composeRule.onNodeWithText(localizedString(SPANISH_TAG, R.string.cancel)).assertIsDisplayed()
    }

    @Test
    fun deleteAccountConfirmationRendersSpanishCopy() {
        val session = deleteAccountSession()
        withTheSpanishAppLocale {
            showInSpanish {
                SettingsSessionPrompts(session = session, callbacks = a11yPromptCallbacks())
            }
            composeRule.waitForIdle()
            val body = localizedString(SPANISH_TAG, R.string.confirm_delete_account)
            assertTrue(body, body.contains("Esto elimina permanentemente tu cuenta"))
            composeRule.scrollToAndAssertDisplayed(hasTextContaining(body))
            composeRule
                .onNodeWithText(localizedString(SPANISH_TAG, R.string.settings_delete_account))
                .assertIsDisplayed()
            composeRule.onNodeWithText(localizedString(SPANISH_TAG, R.string.cancel)).assertIsDisplayed()
        }
    }
}
