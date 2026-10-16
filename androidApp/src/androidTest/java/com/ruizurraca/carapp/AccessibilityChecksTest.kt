package com.ruizurraca.carapp

import android.os.Build
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.ruizurraca.carapp.core.common.SyncStatus
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `E4-02` (`D-216`): the Accessibility Test Framework finds no ERROR on the stateless screens whose
 * states the full app cannot reach deterministically. ATF needs API 34+, so older devices skip.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class AccessibilityChecksTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun enableChecks() {
        composeRule.enableAccessibilityChecks(carAppAccessibilityValidator())
    }

    @Test
    fun signedOutWelcomePassesAccessibilityChecks() {
        val state = a11ySession(SessionPhase.SIGNED_OUT)
        composeRule.setContent {
            CarAppTestTheme { WelcomeScreen(state = state, onGoogle = {}, onContinueWithoutAccount = {}) }
        }
        composeRule.checkAccessibilityOfEveryWindow()
    }

    @Test
    fun busyWelcomeWithAnErrorPassesAccessibilityChecks() {
        val state = a11ySession(SessionPhase.SIGNED_OUT, isBusy = true, message = a11yMessage("AUTH.CANCELLED"))
        composeRule.setContent {
            CarAppTestTheme { WelcomeScreen(state = state, onGoogle = {}, onContinueWithoutAccount = {}) }
        }
        composeRule.checkAccessibilityOfEveryWindow()
    }

    @Test
    fun anonymousReminderBannerPassesAccessibilityChecks() {
        composeRule.setContent { CarAppTestTheme { AnonymousReminderBanner(index = 0, onDismiss = {}) } }
        composeRule.checkAccessibilityOfEveryWindow()
    }

    @Test
    fun localSettingsPassAccessibilityChecks() {
        showSettings(a11ySession(SessionPhase.LOCAL))
        composeRule.checkAccessibilityOfEveryWindow()
    }

    @Test
    fun anonymousSettingsWithTheConversionRowPassAccessibilityChecks() {
        showSettings(a11ySession(SessionPhase.ANONYMOUS))
        composeRule.checkAccessibilityOfEveryWindow()
    }

    @Test
    fun permanentSettingsWithAFailedBackupPassAccessibilityChecks() {
        showSettings(
            session = a11ySession(SessionPhase.PERMANENT),
            sync = a11ySync(SyncStatus.Failed(retryableCount = 1, poisonedCount = 0)),
        )
        composeRule.checkAccessibilityOfEveryWindow()
    }

    @Test
    fun pendingSyncPromptPassesAccessibilityChecks() {
        val session = pendingSyncSession()
        composeRule.setContent {
            CarAppTestTheme { SettingsSessionPrompts(session = session, callbacks = a11yPromptCallbacks()) }
        }
        composeRule.checkAccessibilityOfEveryWindow()
    }

    @Test
    fun deleteLocalDataConfirmationPassesAccessibilityChecks() {
        val session = deleteLocalDataSession()
        composeRule.setContent {
            CarAppTestTheme { SettingsSessionPrompts(session = session, callbacks = a11yPromptCallbacks()) }
        }
        composeRule.checkAccessibilityOfEveryWindow()
    }

    @Test
    fun conversionCollisionConfirmationPassesAccessibilityChecks() {
        showSettings(conversionCollisionSession())
        composeRule.checkAccessibilityOfEveryWindow()
    }

    @Test
    fun departureInProgressPassesAccessibilityChecks() {
        val session = a11ySession(SessionPhase.DELETING, isBusy = true)
        composeRule.setContent { CarAppTestTheme { A11yDepartureScreen(session) } }
        composeRule.checkAccessibilityOfEveryWindow()
    }

    @Test
    fun departureFailureWithRetryPassesAccessibilityChecks() {
        composeRule.setContent { CarAppTestTheme { A11yDepartureScreen(failedDepartureSession()) } }
        composeRule.checkAccessibilityOfEveryWindow()
    }

    private fun showSettings(
        session: SessionUiState,
        sync: SyncUiState = a11ySync(),
    ) {
        composeRule.setContent {
            CarAppTestTheme {
                SettingsScreen(
                    settings = a11ySettings(),
                    session = session,
                    sync = sync,
                    appVersion = A11Y_APP_VERSION,
                    callbacks = a11ySettingsCallbacks(),
                )
            }
        }
    }
}
