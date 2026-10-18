package com.ruizurraca.carapp

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.ruizurraca.carapp.core.common.AuthProvider
import com.ruizurraca.carapp.core.common.SyncStatus
import com.ruizurraca.carapp.core.model.DistanceUnit
import com.ruizurraca.carapp.core.model.VolumeUnit
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * `E4-05`: the conversion surface. The link rows are the stateless Settings screen's; the credential
 * collision is owned by the authenticated root presenter (`SettingsSessionPrompts`), which is mounted
 * beside Settings, so these tests mount both.
 */
class SettingsAccountConversionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun theSettingsScreenOffersLinkingToAnAnonymousSession() {
        showSettings(conversionSession(SessionPhase.ANONYMOUS))

        scrollSettingsTo(SettingsTestTags.LINK_ACCOUNT_ROW)
        composeRule.onNodeWithTag(SettingsTestTags.LINK_ACCOUNT_ROW).assertIsDisplayed()
    }

    @Test
    fun theSettingsScreenNeverOffersLinkingToAPermanentSession() {
        showSettings(conversionSession(SessionPhase.PERMANENT))

        scrollSettingsToEnd()
        composeRule.onNodeWithTag(SettingsTestTags.LINK_ACCOUNT_ROW).assertDoesNotExist()
    }

    @Test
    fun theAuthenticatedRootShowsExactlyOneCollisionConfirmation() {
        showSettings(conversionSession(SessionPhase.ANONYMOUS, message = collisionConfirmation()))

        composeRule.onNodeWithTag(SettingsTestTags.CONVERSION_DIALOG).assertIsDisplayed()
        composeRule
            .onAllNodesWithText(conversionString(R.string.confirm_replace_existing_account))
            .assertCountEquals(1)
    }

    @Test
    fun theAuthenticatedRootRoutesTheCollisionConfirmToTheConversionCallback() {
        val calls = mutableListOf<String>()
        showSettings(
            session = conversionSession(SessionPhase.ANONYMOUS, message = collisionConfirmation()),
            promptCallbacks = recordingSessionPromptCallbacks(calls),
        )

        composeRule.onNodeWithTag(SettingsTestTags.CONVERSION_CONFIRM).performClick()

        assertEquals(listOf("confirmAccountConversion"), calls)
    }

    @Test
    fun theAuthenticatedRootRoutesTheCollisionCancelToTheConversionCallback() {
        val calls = mutableListOf<String>()
        showSettings(
            session = conversionSession(SessionPhase.ANONYMOUS, message = collisionConfirmation()),
            promptCallbacks = recordingSessionPromptCallbacks(calls),
        )

        composeRule.onNodeWithTag(SettingsTestTags.CONVERSION_CANCEL).performClick()

        assertEquals(listOf("cancelAccountConversion"), calls)
    }

    @Test
    fun theSettingsScreenRoutesTheGoogleTapToTheLinkCallback() {
        val requested = mutableListOf<AuthProvider>()
        showSettings(
            session = conversionSession(SessionPhase.ANONYMOUS),
            callbacks = settingsCallbacks(onLinkAccount = { provider -> requested += provider }),
        )

        scrollSettingsTo(SettingsTestTags.LINK_ACCOUNT_GOOGLE)
        composeRule.onNodeWithTag(SettingsTestTags.LINK_ACCOUNT_GOOGLE).performClick()

        assertEquals(listOf(AuthProvider.GOOGLE), requested)
    }

    /** Settings plus the authenticated root presenter, mounted exactly as `VehicleApp` does. */
    private fun showSettings(
        session: SessionUiState,
        callbacks: SettingsCallbacks = settingsCallbacks(),
        promptCallbacks: SessionPromptCallbacks = recordingSessionPromptCallbacks(mutableListOf()),
    ) {
        composeRule.setContent {
            SettingsScreen(
                settings =
                    SettingsUiState(
                        currencyCode = "EUR",
                        supportedCurrencyCodes = listOf("EUR"),
                        distanceUnit = DistanceUnit.KM,
                        volumeUnit = VolumeUnit.LITER,
                        analyticsEnabled = false,
                        isSaving = false,
                        message = null,
                    ),
                session = session,
                sync = SyncUiState(status = SyncStatus.Idle, isOnline = true, message = null),
                appVersion = "0.0.1",
                callbacks = callbacks,
            )
            SettingsSessionPrompts(session = session, callbacks = promptCallbacks)
        }
    }

    private fun settingsCallbacks(onLinkAccount: (AuthProvider) -> Unit = {}): SettingsCallbacks =
        SettingsCallbacks(
            onBack = {},
            onSelectCurrency = {},
            onAnalyticsEnabledChange = {},
            onRetryBackup = {},
            onSignOut = {},
            onDeleteAccount = {},
            onDeleteLocalData = {},
            onDismissSettingsMessage = {},
            onLinkAccount = onLinkAccount,
        )

    /** Lazy and plain scroll containers alike: `performScrollToNode` brings the row into view. */
    private fun scrollSettingsTo(tag: String) {
        val scrollables = composeRule.onAllNodes(hasScrollAction())
        if (scrollables.fetchSemanticsNodes().isNotEmpty()) {
            scrollables.onFirst().performScrollToNode(hasTestTag(tag))
        }
    }

    private fun scrollSettingsToEnd() {
        val scrollables = composeRule.onAllNodes(hasScrollAction())
        if (scrollables.fetchSemanticsNodes().isNotEmpty()) {
            repeat(SCROLL_GESTURES) { scrollables.onFirst().performTouchInput { swipeUp() } }
        }
    }

    private companion object {
        const val SCROLL_GESTURES = 6
    }
}
