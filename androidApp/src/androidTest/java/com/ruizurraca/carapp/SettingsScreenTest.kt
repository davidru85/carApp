package com.ruizurraca.carapp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.ruizurraca.carapp.core.common.SyncStatus
import com.ruizurraca.carapp.core.common.UiMessageKind
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The stateless Settings screen over typed state (`docs/SPECIFICATION.md §3.1`): read-only units, the
 * analytics opt-in, the version, the reused backup indicator and the currency picker. The session rows
 * are covered by `SettingsAccountSectionTest`.
 */
class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun theUnitRowsAreVisibleAndOfferNoClickAction() {
        composeRule.setContent { TestSettingsScreen() }

        composeRule
            .onNodeWithTag(SettingsTestTags.DISTANCE_UNIT_ROW)
            .assertIsDisplayed()
            .assertHasNoClickAction()
            .assertTextContains(targetString(R.string.unit_distance_km))
        composeRule
            .onNodeWithTag(SettingsTestTags.VOLUME_UNIT_ROW)
            .assertIsDisplayed()
            .assertHasNoClickAction()
            .assertTextContains(targetString(R.string.unit_volume_liter))
    }

    @Test
    fun theAnalyticsSwitchIsOffWhenTheStateIsOff() {
        composeRule.setContent { TestSettingsScreen(settings = settingsState(analyticsEnabled = false)) }

        composeRule.onNodeWithTag(SettingsTestTags.ANALYTICS_SWITCH).performScrollTo().assertIsOff()
    }

    @Test
    fun theAnalyticsSwitchIsOnWhenTheStateIsOn() {
        composeRule.setContent { TestSettingsScreen(settings = settingsState(analyticsEnabled = true)) }

        composeRule.onNodeWithTag(SettingsTestTags.ANALYTICS_SWITCH).performScrollTo().assertIsOn()
    }

    @Test
    fun togglingAnalyticsRequestsTheNewValue() {
        val calls = mutableListOf<String>()
        composeRule.setContent { TestSettingsScreen(callbacks = recordingSettingsCallbacks(calls)) }

        composeRule.onNodeWithTag(SettingsTestTags.ANALYTICS_SWITCH).performScrollTo().performClick()

        assertEquals(listOf("analytics:true"), calls)
    }

    @Test
    fun theVersionRowShowsThePackageVersionName() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        @Suppress("DEPRECATION")
        val packageVersionName = context.packageManager.getPackageInfo(context.packageName, 0).versionName
        composeRule.setContent { TestSettingsScreen(appVersion = context.appVersionName()) }

        composeRule
            .onNodeWithTag(SettingsTestTags.VERSION_VALUE, useUnmergedTree = true)
            .performScrollTo()
            .assertTextEquals(checkNotNull(packageVersionName))
    }

    @Test
    fun theBackupRowRendersEachOfTheFourVisuals() {
        var sync by mutableStateOf(syncState(SyncStatus.Idle))
        composeRule.setContent { TestSettingsScreen(sync = sync) }

        listOf(
            SyncStatus.Idle to SyncStatusVisual.IDLE,
            SyncStatus.Syncing to SyncStatusVisual.SYNCING,
            SyncStatus.Pending(count = 2) to SyncStatusVisual.PENDING,
            SyncStatus.Failed(retryableCount = 1, poisonedCount = 0) to SyncStatusVisual.FAILED,
        ).forEach { (status, visual) ->
            sync = syncState(status)
            composeRule.waitForIdle()

            composeRule
                .onNodeWithText(targetString(syncStatusLabelResource(visual)))
                .performScrollTo()
                .assertIsDisplayed()
        }
    }

    @Test
    fun aFailedBackupOffersRetryThatInvokesTheCallback() {
        val calls = mutableListOf<String>()
        composeRule.setContent {
            TestSettingsScreen(
                sync = syncState(SyncStatus.Failed(retryableCount = 1, poisonedCount = 0)),
                callbacks = recordingSettingsCallbacks(calls),
            )
        }

        composeRule.onNodeWithTag(SyncStatusTestTags.RETRY).performScrollTo().performClick()

        assertEquals(listOf("retryBackup"), calls)
    }

    @Test
    fun theCurrencyRowShowsThePlatformNameAndCode() {
        val locale =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext.resources.configuration.locales[0]
        val expected = targetString(R.string.settings_currency_value, currencyDisplayName("EUR", locale), "EUR")
        composeRule.setContent { TestSettingsScreen(settings = settingsState(currencyCode = "EUR")) }

        composeRule
            .onNodeWithTag(SettingsTestTags.CURRENCY_VALUE, useUnmergedTree = true)
            .assertTextEquals(expected)
    }

    @Test
    fun theCurrencyRowIsNotActionableBeforeSettingsLoad() {
        composeRule.setContent { TestSettingsScreen(settings = settingsState(currencyCode = null)) }

        composeRule.onNodeWithTag(SettingsTestTags.CURRENCY_ROW).assertHasNoClickAction()
    }

    @Test
    fun pickingACurrencyReportsItsCode() {
        val calls = mutableListOf<String>()
        composeRule.setContent { TestSettingsScreen(callbacks = recordingSettingsCallbacks(calls)) }

        composeRule.onNodeWithTag(SettingsTestTags.CURRENCY_ROW).assertHasClickAction().performClick()
        composeRule.onNodeWithTag(SettingsTestTags.currencyOption("GBP")).performScrollTo().performClick()

        assertEquals(listOf("currency:GBP"), calls)
        composeRule.onNodeWithTag(SettingsTestTags.CURRENCY_PICKER).assertDoesNotExist()
    }

    @Test
    fun aSettingsErrorIsRenderedThroughTheSharedMapping() {
        val failure = uiMessage("PERSISTENCE.TRANSACTION_FAILED", UiMessageKind.ERROR, null)
        composeRule.setContent { TestSettingsScreen(settings = settingsState(message = failure)) }

        composeRule
            .onNodeWithTag(SettingsTestTags.ERROR)
            .assertTextEquals(targetString(R.string.error_persistence))
    }

    @Test
    fun aSettingsErrorCanBeDismissed() {
        val calls = mutableListOf<String>()
        val failure = uiMessage("PERSISTENCE.TRANSACTION_FAILED", UiMessageKind.ERROR, null)
        composeRule.setContent {
            TestSettingsScreen(
                settings = settingsState(message = failure),
                callbacks = recordingSettingsCallbacks(calls),
            )
        }

        composeRule.onNodeWithTag(SettingsTestTags.DISMISS_ERROR).performClick()

        assertEquals(listOf("dismissSettingsMessage"), calls)
    }

    /**
     * `R3` U0: the stored settings are unknown, so no row may present a placeholder as a known value and
     * no switch may claim a stored opt-out (`docs/CONTRACTS.md §20.10`).
     */
    @Test
    fun unknownSettingsRenderLoadingValuesAndNoAnalyticsSwitch() {
        val calls = mutableListOf<String>()
        composeRule.setContent {
            TestSettingsScreen(
                settings = settingsState(currencyCode = null),
                callbacks = recordingSettingsCallbacks(calls),
            )
        }

        listOf(
            SettingsTestTags.CURRENCY_VALUE,
            SettingsTestTags.DISTANCE_UNIT_ROW,
            SettingsTestTags.VOLUME_UNIT_ROW,
            SettingsTestTags.ANALYTICS_LOADING,
        ).forEach { tag ->
            composeRule.onNodeWithTag(tag, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        }
        composeRule.onNodeWithTag(SettingsTestTags.ANALYTICS_SWITCH).assertDoesNotExist()
        val loading = targetString(R.string.loading)
        composeRule
            .onNodeWithTag(SettingsTestTags.CURRENCY_VALUE, useUnmergedTree = true)
            .assertTextEquals(loading)
        listOf(SettingsTestTags.DISTANCE_UNIT_ROW, SettingsTestTags.VOLUME_UNIT_ROW).forEach { tag ->
            composeRule.onNodeWithTag(tag).assertTextContains(loading)
        }
        assertEquals(emptyList<String>(), calls)
    }

    /** `R3` U1: a read failure is not a completed read, so the loading values and the typed error stay. */
    @Test
    fun failedSettingsReadRendersLoadingValuesPlusTheTypedError() {
        val calls = mutableListOf<String>()
        val failure = uiMessage("PERSISTENCE.TRANSACTION_FAILED", UiMessageKind.ERROR, null)
        composeRule.setContent {
            TestSettingsScreen(
                settings = settingsState(currencyCode = null, message = failure),
                callbacks = recordingSettingsCallbacks(calls),
            )
        }

        listOf(
            SettingsTestTags.ERROR,
            SettingsTestTags.ANALYTICS_LOADING,
        ).forEach { tag -> composeRule.onNodeWithTag(tag).performScrollTo().assertIsDisplayed() }
        composeRule
            .onNodeWithTag(SettingsTestTags.ERROR)
            .assertTextEquals(targetString(R.string.error_persistence))
        composeRule.onNodeWithTag(SettingsTestTags.ANALYTICS_SWITCH).assertDoesNotExist()
        assertEquals(emptyList<String>(), calls)
    }

    /** `R3` K0: a known off preference is the real stored state and is actionable. */
    @Test
    fun knownOffAnalyticsRendersAnEnabledOffSwitch() {
        composeRule.setContent { TestSettingsScreen(settings = settingsState(analyticsEnabled = false)) }

        composeRule
            .onNodeWithTag(SettingsTestTags.ANALYTICS_SWITCH)
            .performScrollTo()
            .assertIsOff()
            .assertIsEnabled()
        composeRule.onNodeWithTag(SettingsTestTags.ANALYTICS_LOADING).assertDoesNotExist()
    }

    /** `R3` K1: a stored opt-in is never overridden by an initial default. */
    @Test
    fun knownOnAnalyticsRendersAnEnabledOnSwitch() {
        composeRule.setContent { TestSettingsScreen(settings = settingsState(analyticsEnabled = true)) }

        composeRule
            .onNodeWithTag(SettingsTestTags.ANALYTICS_SWITCH)
            .performScrollTo()
            .assertIsOn()
            .assertIsEnabled()
        composeRule.onNodeWithTag(SettingsTestTags.ANALYTICS_LOADING).assertDoesNotExist()
    }

    /** `R3` K2: a write in flight disables the switch, so it cannot start a second write. */
    @Test
    fun savingAnalyticsRendersACheckedDisabledSwitchThatDoesNotWrite() {
        val calls = mutableListOf<String>()
        composeRule.setContent {
            TestSettingsScreen(
                settings = settingsState(analyticsEnabled = true, isSaving = true),
                callbacks = recordingSettingsCallbacks(calls),
            )
        }

        composeRule
            .onNodeWithTag(SettingsTestTags.ANALYTICS_SWITCH)
            .performScrollTo()
            .assertIsOn()
            .assertIsNotEnabled()
        composeRule.onNodeWithTag(SettingsTestTags.ANALYTICS_SWITCH).performClick()

        assertEquals(emptyList<String>(), calls)
    }

    /** Only `UiMessageKind.ERROR` is raised as a failure, as on the departure prompts. */
    @Test
    fun aNonErrorSettingsMessageIsNotRaisedAsAFailure() {
        val notice = uiMessage("AUTH.NETWORK_UNAVAILABLE", UiMessageKind.WARNING, null)
        composeRule.setContent { TestSettingsScreen(settings = settingsState(message = notice)) }

        composeRule.onNodeWithTag(SettingsTestTags.ERROR).assertDoesNotExist()
        composeRule.onNodeWithTag(SettingsTestTags.DISMISS_ERROR).assertDoesNotExist()
    }
}
