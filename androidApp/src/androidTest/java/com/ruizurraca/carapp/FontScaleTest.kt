package com.ruizurraca.carapp

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `E4-02`: at a 200% font scale (`docs/SPECIFICATION.md §11`) every primary action and every field of
 * each screen can be scrolled to and is displayed, and every dialog keeps its text and both actions.
 */
@RunWith(AndroidJUnit4::class)
class FontScaleTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun welcomeActionsStayReachableAtDoubleFontScale() {
        showAtDoubleFontScale {
            WelcomeScreen(state = a11ySession(SessionPhase.SIGNED_OUT), onGoogle = {}, onContinueWithoutAccount = {})
        }
        listOf(OnboardingTestTags.GOOGLE, OnboardingTestTags.GUEST).forEach(composeRule::scrollToAndAssertDisplayed)
    }

    @Test
    fun vehicleFormActionsStayReachableAtDoubleFontScale() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val holder = newA11yVehicleFormHolder(scope)
        try {
            showAtDoubleFontScale {
                VehicleFormScreen(stateHolder = holder, originalVehicleId = null, onBack = {}, onSaved = {})
            }
            listOf(
                VehicleTestTags.BACK,
                VehicleTestTags.NAME,
                VehicleTestTags.ODOMETER,
                VehicleTestTags.FUEL_TYPE_INPUT,
                VehicleTestTags.SAVE,
            ).forEach(composeRule::scrollToAndAssertDisplayed)
        } finally {
            holder.close()
            scope.cancel()
        }
    }

    @Test
    fun vehicleDetailActionsStayReachableAtDoubleFontScale() {
        showAtDoubleFontScale {
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
        listOf(
            VehicleTestTags.DETAIL_NAME,
            VehicleTestTags.EDIT,
            VehicleTestTags.DELETE,
            FuelEntryTestTags.CONSUMPTION_AVERAGE,
            FuelEntryTestTags.row(A11Y_ENTRY_ID),
            FuelEntryTestTags.ADD_FUEL_ENTRY,
        ).forEach(composeRule::scrollToAndAssertDisplayed)
    }

    @Test
    fun vehicleDeleteConfirmationStaysReadableAtDoubleFontScale() {
        showAtDoubleFontScale { VehicleDeleteConfirmation(onDismiss = {}, onConfirm = {}) }
        composeRule.scrollToAndAssertDisplayed(hasTextContaining(targetString(R.string.delete_vehicle_confirmation)))
        listOf(VehicleTestTags.CONFIRM_DELETE_VEHICLE, VehicleTestTags.CANCEL_DELETE_VEHICLE)
            .forEach(composeRule::scrollToAndAssertDisplayed)
    }

    @Test
    fun fuelEntryFormActionsStayReachableAtDoubleFontScale() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val holder = newA11yFuelEntryFormHolder(scope)
        try {
            showAtDoubleFontScale { FuelEntryFormScreen(stateHolder = holder, onBack = {}, onSaved = {}) }
            listOf(
                FuelEntryTestTags.CLOSE,
                FuelEntryTestTags.SAVE,
                FuelEntryTestTags.DATE,
                FuelEntryTestTags.ODOMETER,
                FuelEntryTestTags.LITERS,
                FuelEntryTestTags.PRICE_PER_LITER,
                FuelEntryTestTags.DERIVED_VALUE,
                FuelEntryTestTags.FULL_TANK,
                FuelEntryTestTags.MISSED_ENTRIES,
                FuelEntryTestTags.NOTES,
            ).forEach(composeRule::scrollToAndAssertDisplayed)
        } finally {
            holder.close()
            scope.cancel()
        }
    }

    @Test
    fun odometerWarningStaysReadableAtDoubleFontScale() {
        showAtDoubleFontScale { FuelOdometerWarningDialog(onDismiss = {}, onConfirm = {}) }
        composeRule.scrollToAndAssertDisplayed(hasTextContaining(targetString(R.string.odometer_warning_body)))
        listOf(FuelEntryTestTags.CONFIRM_ODOMETER_WARNING, FuelEntryTestTags.CANCEL_ODOMETER_WARNING)
            .forEach(composeRule::scrollToAndAssertDisplayed)
    }

    @Test
    fun localSettingsRowsStayReachableAtDoubleFontScale() {
        assertSettingsReachable(a11ySession(SessionPhase.LOCAL))
    }

    @Test
    fun anonymousSettingsRowsAndTheConversionRowStayReachableAtDoubleFontScale() {
        val reached = assertSettingsReachable(a11ySession(SessionPhase.ANONYMOUS))
        assertTrue("the E4-05 row was not rendered: $reached", SettingsTestTags.LINK_ACCOUNT_ROW in reached)
    }

    @Test
    fun permanentSettingsRowsStayReachableAtDoubleFontScale() {
        assertSettingsReachable(a11ySession(SessionPhase.PERMANENT))
    }

    @Test
    fun conversionCollisionConfirmationStaysReadableAtDoubleFontScale() {
        assertSettingsReachable(conversionCollisionSession())
        val confirmation = targetString(R.string.confirm_replace_existing_account)
        composeRule.scrollToAndAssertDisplayed(hasTextContaining(confirmation))
    }

    @Test
    fun departureActionsStayReachableAtDoubleFontScale() {
        showAtDoubleFontScale { A11yDepartureScreen(failedDepartureSession()) }
        val reached = composeRule.assertRenderedTagsReachable(stringConstantsOf(DepartureTestTags::class.java))
        assertTrue("no departure action was rendered", reached.isNotEmpty())
        composeRule.scrollToAndAssertDisplayed(hasTextContaining(targetString(R.string.error_persistence)))
    }

    @Test
    fun pendingSyncPromptStaysReadableAtDoubleFontScale() {
        showAtDoubleFontScale {
            SettingsSessionPrompts(session = pendingSyncSession(), callbacks = a11yPromptCallbacks())
        }
        val reached = composeRule.assertRenderedTagsReachable(stringConstantsOf(SettingsTestTags::class.java))
        assertTrue("the wait, cancel and discard actions were not all rendered: $reached", reached.size >= 3)
    }

    @Test
    fun deleteLocalDataConfirmationStaysReadableAtDoubleFontScale() {
        showAtDoubleFontScale {
            SettingsSessionPrompts(session = deleteLocalDataSession(), callbacks = a11yPromptCallbacks())
        }
        composeRule.assertRenderedTagsReachable(stringConstantsOf(SettingsTestTags::class.java))
        composeRule.scrollToAndAssertDisplayed(hasTextContaining(targetString(R.string.confirm_delete_local_data)))
    }

    private fun assertSettingsReachable(session: SessionUiState): List<String> {
        showAtDoubleFontScale {
            SettingsScreen(
                settings = a11ySettings(),
                session = session,
                sync = a11ySync(),
                appVersion = A11Y_APP_VERSION,
                callbacks = a11ySettingsCallbacks(),
            )
        }
        val reached = composeRule.assertRenderedTagsReachable(stringConstantsOf(SettingsTestTags::class.java))
        assertTrue("only $reached were rendered", reached.size >= MIN_SETTINGS_ELEMENTS)
        return reached
    }

    private fun showAtDoubleFontScale(content: @Composable () -> Unit) {
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(DOUBLE_FONT_SCALE)) {
                CarAppTestTheme(content)
            }
        }
    }

    private companion object {
        /** Currency, the two units, the backup status, the analytics switch and the version (`§3.1`). */
        const val MIN_SETTINGS_ELEMENTS = 4
    }
}
