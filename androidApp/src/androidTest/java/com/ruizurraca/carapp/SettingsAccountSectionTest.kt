package com.ruizurraca.carapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The B1-D5 session rows: a permanent owner signs out or deletes the account, a local or anonymous
 * owner deletes local data (`docs/CONTRACTS.md §11.5`), and every row only requests its intent.
 */
class SettingsAccountSectionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun aPermanentSessionShowsSignOutAndDeleteAccountButNoLocalDataRow() {
        composeRule.setContent { TestSettingsScreen(session = sessionState(SessionPhase.PERMANENT)) }

        composeRule.onNodeWithTag(SettingsTestTags.SIGN_OUT_ROW).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.DELETE_ACCOUNT_ROW).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.DELETE_LOCAL_DATA_ROW).assertDoesNotExist()
    }

    @Test
    fun anAnonymousSessionShowsOnlyTheDeleteLocalDataRow() {
        composeRule.setContent { TestSettingsScreen(session = sessionState(SessionPhase.ANONYMOUS)) }

        composeRule.onNodeWithTag(SettingsTestTags.DELETE_LOCAL_DATA_ROW).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.SIGN_OUT_ROW).assertDoesNotExist()
        composeRule.onNodeWithTag(SettingsTestTags.DELETE_ACCOUNT_ROW).assertDoesNotExist()
    }

    @Test
    fun aLocalSessionShowsOnlyTheDeleteLocalDataRow() {
        composeRule.setContent { TestSettingsScreen(session = sessionState(SessionPhase.LOCAL)) }

        composeRule.onNodeWithTag(SettingsTestTags.DELETE_LOCAL_DATA_ROW).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.SIGN_OUT_ROW).assertDoesNotExist()
        composeRule.onNodeWithTag(SettingsTestTags.DELETE_ACCOUNT_ROW).assertDoesNotExist()
    }

    @Test
    fun sessionRowsAreDisabledWhileTheSessionIsBusy() {
        composeRule.setContent { TestSettingsScreen(session = sessionState(SessionPhase.PERMANENT, isBusy = true)) }

        composeRule.onNodeWithTag(SettingsTestTags.SESSION_PROGRESS).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.SIGN_OUT_ROW).assertIsNotEnabled()
        composeRule.onNodeWithTag(SettingsTestTags.DELETE_ACCOUNT_ROW).assertIsNotEnabled()
    }

    @Test
    fun theSignOutRowRequestsSignOut() {
        val calls = mutableListOf<String>()
        composeRule.setContent {
            TestSettingsScreen(
                session = sessionState(SessionPhase.PERMANENT),
                callbacks = recordingSettingsCallbacks(calls),
            )
        }

        composeRule.onNodeWithTag(SettingsTestTags.SIGN_OUT_ROW).performScrollTo().performClick()

        assertEquals(listOf("signOut"), calls)
    }

    @Test
    fun theDeleteAccountRowRequestsAccountDeletion() {
        val calls = mutableListOf<String>()
        composeRule.setContent {
            TestSettingsScreen(
                session = sessionState(SessionPhase.PERMANENT),
                callbacks = recordingSettingsCallbacks(calls),
            )
        }

        composeRule.onNodeWithTag(SettingsTestTags.DELETE_ACCOUNT_ROW).performScrollTo().performClick()

        assertEquals(listOf("deleteAccount"), calls)
    }

    @Test
    fun theDeleteLocalDataRowRequestsLocalDataDeletion() {
        val calls = mutableListOf<String>()
        composeRule.setContent {
            TestSettingsScreen(
                session = sessionState(SessionPhase.ANONYMOUS),
                callbacks = recordingSettingsCallbacks(calls),
            )
        }

        composeRule.onNodeWithTag(SettingsTestTags.DELETE_LOCAL_DATA_ROW).performScrollTo().performClick()

        assertEquals(listOf("deleteLocalData"), calls)
    }
}
