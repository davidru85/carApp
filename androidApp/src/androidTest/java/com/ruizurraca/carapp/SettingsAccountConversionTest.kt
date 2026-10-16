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

/** `E4-05`: the conversion surface as the stateless Settings screen of `E4-01` renders it. */
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
    fun theSettingsScreenShowsExactlyOneCollisionConfirmation() {
        showSettings(conversionSession(SessionPhase.ANONYMOUS, message = collisionConfirmation()))

        composeRule.onNodeWithTag(SettingsTestTags.CONVERSION_DIALOG).assertIsDisplayed()
        composeRule
            .onAllNodesWithText(conversionString(R.string.confirm_replace_existing_account))
            .assertCountEquals(1)
    }

    @Test
    fun theSettingsScreenRoutesTheCollisionConfirmToTheConversionCallback() {
        var confirms = 0
        showSettings(
            session = conversionSession(SessionPhase.ANONYMOUS, message = collisionConfirmation()),
            callbacks = settingsCallbacks(onConfirmAccountConversion = { confirms += 1 }),
        )

        composeRule.onNodeWithTag(SettingsTestTags.CONVERSION_CONFIRM).performClick()

        assertEquals(1, confirms)
    }

    @Test
    fun theSettingsScreenRoutesTheCollisionCancelToTheConversionCallback() {
        var cancels = 0
        showSettings(
            session = conversionSession(SessionPhase.ANONYMOUS, message = collisionConfirmation()),
            callbacks = settingsCallbacks(onCancelAccountConversion = { cancels += 1 }),
        )

        composeRule.onNodeWithTag(SettingsTestTags.CONVERSION_CANCEL).performClick()

        assertEquals(1, cancels)
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

    private fun showSettings(
        session: SessionUiState,
        callbacks: SettingsCallbacks = settingsCallbacks(),
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
        }
    }

    private fun settingsCallbacks(
        onLinkAccount: (AuthProvider) -> Unit = {},
        onConfirmAccountConversion: () -> Unit = {},
        onCancelAccountConversion: () -> Unit = {},
    ): SettingsCallbacks =
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
            onConfirmAccountConversion = onConfirmAccountConversion,
            onCancelAccountConversion = onCancelAccountConversion,
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
