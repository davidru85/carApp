package com.ruizurraca.carapp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ruizurraca.carapp.core.common.AuthProvider
import com.ruizurraca.carapp.core.common.Confirmation
import com.ruizurraca.carapp.core.common.UiMessage
import com.ruizurraca.carapp.core.common.UiMessageKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * `E4-05`: the stateless conversion surface. Each test composes once; the phase test varies the
 * published session through a mutable state, because a Compose test rule accepts a single
 * `setContent` per test.
 */
class AccountConversionSurfaceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun anAnonymousSessionSeesTheLinkAccountRow() {
        composeRule.setContent {
            LinkAccountSection(session = conversionSession(SessionPhase.ANONYMOUS), onLinkAccount = {})
        }

        composeRule.onNodeWithTag(SettingsTestTags.LINK_ACCOUNT_ROW).assertIsDisplayed()
    }

    @Test
    fun noOtherSessionPhaseSeesTheLinkAccountRow() {
        var phase: SessionPhase by mutableStateOf(SessionPhase.LOCAL)
        composeRule.setContent {
            LinkAccountSection(session = conversionSession(phase), onLinkAccount = {})
        }

        SessionPhase.entries.filterNot { it == SessionPhase.ANONYMOUS }.forEach { published ->
            phase = published
            composeRule.waitForIdle()

            composeRule.onNodeWithTag(SettingsTestTags.LINK_ACCOUNT_ROW).assertDoesNotExist()
        }
    }

    @Test
    fun androidOffersGoogleAsTheOnlyLinkProvider() {
        composeRule.setContent {
            LinkAccountSection(session = conversionSession(SessionPhase.ANONYMOUS), onLinkAccount = {})
        }

        composeRule
            .onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag(SettingsTestTags.LINK_ACCOUNT_ROW)))
            .assertCountEquals(1)
        composeRule
            .onNodeWithTag(SettingsTestTags.LINK_ACCOUNT_GOOGLE)
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun tappingGoogleRequestsAGoogleLink() {
        val requested = mutableListOf<AuthProvider>()
        composeRule.setContent {
            LinkAccountSection(
                session = conversionSession(SessionPhase.ANONYMOUS),
                onLinkAccount = { provider -> requested += provider },
            )
        }

        composeRule.onNodeWithTag(SettingsTestTags.LINK_ACCOUNT_GOOGLE).performClick()

        assertEquals(listOf(AuthProvider.GOOGLE), requested)
    }

    @Test
    fun aBusySessionBlocksRepeatedLinkTaps() {
        val requested = mutableListOf<AuthProvider>()
        composeRule.setContent {
            LinkAccountSection(
                session = conversionSession(SessionPhase.ANONYMOUS, isBusy = true),
                onLinkAccount = { provider -> requested += provider },
            )
        }

        composeRule
            .onNodeWithTag(SettingsTestTags.LINK_ACCOUNT_GOOGLE)
            .assertIsNotEnabled()
            .performClick()

        assertTrue("A busy session must not start a second link", requested.isEmpty())
    }

    @Test
    fun aBusySessionShowsTheLabelledLinkProgress() {
        composeRule.setContent {
            LinkAccountSection(
                session = conversionSession(SessionPhase.ANONYMOUS, isBusy = true),
                onLinkAccount = {},
            )
        }

        composeRule.onNodeWithTag(SettingsTestTags.LINK_ACCOUNT_PROGRESS).assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription(conversionString(R.string.settings_link_account_in_progress))
            .assertIsDisplayed()
    }

    @Test
    fun theCollisionConfirmationStatesTheReplacement() {
        composeRule.setContent {
            AccountConversionCollisionDialog(message = collisionConfirmation(), onConfirm = {}, onCancel = {})
        }

        composeRule.onNodeWithTag(SettingsTestTags.CONVERSION_DIALOG).assertIsDisplayed()
        composeRule
            .onNodeWithText(conversionString(R.string.confirm_replace_existing_account))
            .assertIsDisplayed()
    }

    @Test
    fun confirmingTheCollisionInvokesOnlyTheConfirmCallback() {
        var confirms = 0
        var cancels = 0
        composeRule.setContent {
            AccountConversionCollisionDialog(
                message = collisionConfirmation(),
                onConfirm = { confirms += 1 },
                onCancel = { cancels += 1 },
            )
        }

        composeRule.onNodeWithTag(SettingsTestTags.CONVERSION_CONFIRM).performClick()

        assertEquals(1, confirms)
        assertEquals(0, cancels)
    }

    @Test
    fun cancellingTheCollisionInvokesOnlyTheCancelCallback() {
        var confirms = 0
        var cancels = 0
        composeRule.setContent {
            AccountConversionCollisionDialog(
                message = collisionConfirmation(),
                onConfirm = { confirms += 1 },
                onCancel = { cancels += 1 },
            )
        }

        composeRule.onNodeWithTag(SettingsTestTags.CONVERSION_CANCEL).performClick()

        assertEquals(0, confirms)
        assertEquals(1, cancels)
    }

    @Test
    fun noOtherSessionMessageOpensTheCollisionDialog() {
        composeRule.setContent {
            AccountConversionCollisionDialog(
                message =
                    UiMessage(
                        id = 6L,
                        kind = UiMessageKind.WARNING,
                        code = "CONFIRMATION.DeleteLocalData",
                        confirmation = Confirmation.DeleteLocalData,
                    ),
                onConfirm = {},
                onCancel = {},
            )
        }

        composeRule.onNodeWithTag(SettingsTestTags.CONVERSION_DIALOG).assertDoesNotExist()
    }

    @Test
    fun aRemoteFailureOnTheSessionChannelRendersTheReplacementCopy() {
        composeRule.setContent {
            SessionErrorText(
                message =
                    UiMessage(
                        id = 2L,
                        kind = UiMessageKind.ERROR,
                        code = "REMOTE.UNAVAILABLE",
                        confirmation = null,
                    ),
                testTag = SESSION_ERROR_TAG,
            )
        }

        composeRule
            .onNodeWithText(conversionString(R.string.error_conversion_replacement_pending))
            .assertIsDisplayed()
    }

    @Test
    fun aConfirmationIsNeverRenderedAsSessionErrorText() {
        composeRule.setContent {
            SessionErrorText(message = collisionConfirmation(), testTag = SESSION_ERROR_TAG)
        }

        composeRule.onNodeWithTag(SESSION_ERROR_TAG).assertDoesNotExist()
    }

    private companion object {
        const val SESSION_ERROR_TAG = "settings_conversion_test_session_error"
    }
}
