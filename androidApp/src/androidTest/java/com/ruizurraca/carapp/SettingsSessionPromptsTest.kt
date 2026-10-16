package com.ruizurraca.carapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ruizurraca.carapp.core.common.Confirmation
import com.ruizurraca.carapp.core.common.UiMessageKind
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The F-5 prompts over typed session state (`docs/CONTRACTS.md §20.10`): the pending-sync warning with
 * its exact count and three answers, the two destructive confirmations and `ERROR` departure failures.
 * Re-authentication belongs to `DepartureScreen`. Each answer only invokes its callback; the binding to
 * intents is `sessionPromptCallbacks`.
 */
class SettingsSessionPromptsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun thePendingSyncPromptShowsTheExactCount() {
        showPrompts(pendingSync(count = 3))

        composeRule.onNodeWithTag(SettingsTestTags.PENDING_SYNC_DIALOG).assertIsDisplayed()
        composeRule
            .onNodeWithTag(SettingsTestTags.PENDING_SYNC_BODY)
            .assertTextEquals(targetQuantityString(R.plurals.pending_sync_body, 3))
    }

    @Test
    fun aSinglePendingChangeUsesTheSingularCopy() {
        showPrompts(pendingSync(count = 1))

        composeRule
            .onNodeWithTag(SettingsTestTags.PENDING_SYNC_BODY)
            .assertTextEquals(targetQuantityString(R.plurals.pending_sync_body, 1))
    }

    @Test
    fun waitingForTheBackupInvokesTheWaitAction() {
        val calls = showPrompts(pendingSync(count = 2))

        composeRule.onNodeWithTag(SettingsTestTags.PENDING_SYNC_WAIT).performClick()

        assertEquals(listOf("wait"), calls)
    }

    @Test
    fun cancellingThePendingSyncPromptWithdrawsIt() {
        val calls = showPrompts(pendingSync(count = 2))

        composeRule.onNodeWithTag(SettingsTestTags.PENDING_SYNC_CANCEL).performClick()

        assertEquals(listOf("withdraw"), calls)
    }

    @Test
    fun discardingPendingChangesInvokesTheDiscardAction() {
        val calls = showPrompts(pendingSync(count = 2))

        composeRule.onNodeWithTag(SettingsTestTags.PENDING_SYNC_DISCARD).performClick()

        assertEquals(listOf("discard"), calls)
    }

    @Test
    fun theAccountDeletionConfirmationConfirmsDestructively() {
        val message = uiMessage("CONFIRMATION.DeleteAccount", UiMessageKind.WARNING, Confirmation.DeleteAccount)
        val calls = showPrompts(sessionState(SessionPhase.PERMANENT, message = message))

        composeRule.onNodeWithTag(SettingsTestTags.CONFIRM_DELETE_ACCOUNT_DIALOG).assertIsDisplayed()
        composeRule.onNodeWithText(targetString(R.string.confirm_delete_account)).assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.CONFIRM_DESTRUCTIVE).performClick()

        assertEquals(listOf("confirmDeleteAccount"), calls)
    }

    @Test
    fun theLocalDataDeletionConfirmationConfirmsDestructively() {
        val message = uiMessage("CONFIRMATION.DeleteLocalData", UiMessageKind.WARNING, Confirmation.DeleteLocalData)
        val calls = showPrompts(sessionState(SessionPhase.ANONYMOUS, message = message))

        composeRule.onNodeWithTag(SettingsTestTags.CONFIRM_DELETE_LOCAL_DATA_DIALOG).assertIsDisplayed()
        composeRule.onNodeWithText(targetString(R.string.confirm_delete_local_data)).assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.CONFIRM_DESTRUCTIVE).performClick()

        assertEquals(listOf("confirmDeleteLocalData"), calls)
    }

    @Test
    fun cancellingADestructiveConfirmationWithdrawsIt() {
        val message = uiMessage("CONFIRMATION.DeleteLocalData", UiMessageKind.WARNING, Confirmation.DeleteLocalData)
        val calls = showPrompts(sessionState(SessionPhase.LOCAL, message = message))

        composeRule.onNodeWithTag(SettingsTestTags.CANCEL_DESTRUCTIVE).performClick()

        assertEquals(listOf("withdraw"), calls)
    }

    /** B1-D4: a stale login is carried by the departure surface, so no dialog is raised for it here. */
    @Test
    fun aStaleLoginIsLeftToTheDepartureSurface() {
        showPrompts(staleLogin())

        composeRule.onNodeWithTag(SettingsTestTags.SESSION_ERROR_DIALOG).assertDoesNotExist()
        composeRule.onNodeWithText(targetString(R.string.error_auth_requires_recent_login)).assertDoesNotExist()
    }

    @Test
    fun aDepartureErrorIsShownWithItsMappedCopy() {
        showPrompts(remoteDeletionFailure())

        composeRule
            .onNodeWithTag(SettingsTestTags.SESSION_ERROR_TEXT)
            .assertTextEquals(targetString(R.string.error_account_deletion_remote_failed))
    }

    @Test
    fun aDepartureErrorCanBeDismissed() {
        val calls = showPrompts(remoteDeletionFailure())

        composeRule.onNodeWithTag(SettingsTestTags.SESSION_ERROR_DISMISS).performClick()

        assertEquals(listOf("dismissError"), calls)
    }

    /** The F-1 local start notice (`StateHolders.kt:172-193`) is not a departure and shows no prompt. */
    @Test
    fun theLocalStartNoticeShowsNoPrompt() {
        val notice = uiMessage("AUTH.NETWORK_UNAVAILABLE", UiMessageKind.WARNING, null)
        showPrompts(sessionState(SessionPhase.LOCAL, message = notice))

        composeRule.onNodeWithTag(SettingsTestTags.PENDING_SYNC_DIALOG).assertDoesNotExist()
        composeRule.onNodeWithTag(SettingsTestTags.CONFIRM_DELETE_ACCOUNT_DIALOG).assertDoesNotExist()
        composeRule.onNodeWithTag(SettingsTestTags.CONFIRM_DELETE_LOCAL_DATA_DIALOG).assertDoesNotExist()
        composeRule.onNodeWithTag(SettingsTestTags.SESSION_ERROR_DIALOG).assertDoesNotExist()
    }

    private fun showPrompts(session: SessionUiState): List<String> {
        val calls = mutableListOf<String>()
        composeRule.setContent {
            SettingsSessionPrompts(session = session, callbacks = recordingSessionPromptCallbacks(calls))
        }
        return calls
    }

    private fun pendingSync(count: Int): SessionUiState =
        sessionState(
            phase = SessionPhase.PERMANENT,
            message = uiMessage("WARNING.PENDING_SYNC", UiMessageKind.WARNING, Confirmation.DiscardPendingChanges),
            pendingSyncCount = count,
        )

    private fun staleLogin(): SessionUiState =
        sessionState(
            phase = SessionPhase.PERMANENT,
            message = uiMessage("AUTH.REQUIRES_RECENT_LOGIN", UiMessageKind.ERROR, null),
        )

    private fun remoteDeletionFailure(): SessionUiState =
        sessionState(
            phase = SessionPhase.PERMANENT,
            message = uiMessage("AUTH.ACCOUNT_DELETION_REMOTE_FAILED", UiMessageKind.ERROR, null),
        )
}
