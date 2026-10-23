package com.ruizurraca.carapp

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ruizurraca.carapp.core.common.AuthProvider
import com.ruizurraca.carapp.core.common.UiMessageKind
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * D-214: progress while a departure or a re-authentication attempt runs; the `§20.10`
 * re-authentication choice for a stale login; the typed failure, what a retry repeats and the
 * `retryDeparture()` action once a step failed; and no retry when nothing is retained.
 */
class DepartureScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun aRunningDepartureShowsProgressAndNoRetry() {
        showDeparture(sessionState(SessionPhase.DELETING, isBusy = true))

        composeRule.onNodeWithTag(DepartureTestTags.PROGRESS).assertIsDisplayed()
        composeRule.onNodeWithTag(DepartureTestTags.RETRY).assertDoesNotExist()
        composeRule.onNodeWithTag(DepartureTestTags.FAILURE_TITLE).assertDoesNotExist()
    }

    @Test
    fun aFailedLocalClearShowsTheTypedFailure() {
        showDeparture(failedLocalClear())

        composeRule.onNodeWithTag(DepartureTestTags.FAILURE_TITLE).assertIsDisplayed()
        composeRule
            .onNodeWithTag(DepartureTestTags.ERROR)
            .assertTextEquals(targetString(R.string.error_persistence))
        composeRule
            .onNodeWithTag(DepartureTestTags.RETRY_EXPLANATION)
            .assertTextEquals(targetString(R.string.departure_retry_local_clear))
    }

    @Test
    fun theRetryActionRepeatsTheRetainedStep() {
        val calls = showDeparture(failedLocalClear())

        composeRule.onNodeWithTag(DepartureTestTags.RETRY).assertIsDisplayed().performClick()

        assertEquals(listOf("retry"), calls)
    }

    @Test
    fun aFailedSessionCleanupExplainsWhatTheRetryRepeats() {
        showDeparture(
            sessionState(
                phase = SessionPhase.PERMANENT,
                message = uiMessage("AUTH.NETWORK_UNAVAILABLE", UiMessageKind.ERROR, null),
                pendingDepartureRetry = DepartureRetry.SESSION_CLEANUP,
            ),
        )

        composeRule
            .onNodeWithTag(DepartureTestTags.RETRY_EXPLANATION)
            .assertTextEquals(targetString(R.string.departure_retry_session_cleanup))
    }

    @Test
    fun theFailureMessageCanBeDismissed() {
        val calls = showDeparture(failedLocalClear())

        composeRule.onNodeWithTag(DepartureTestTags.DISMISS).performClick()

        assertEquals(listOf("dismiss"), calls)
    }

    /** `§20.10`: `retryDeparture()` does nothing while `pendingDepartureRetry` is `null`, so none is offered. */
    @Test
    fun aFailureWithoutARetainedStepOffersNoRetry() {
        showDeparture(
            sessionState(
                phase = SessionPhase.DELETING,
                message = uiMessage("PERSISTENCE.TRANSACTION_FAILED", UiMessageKind.ERROR, null),
            ),
        )

        composeRule.onNodeWithTag(DepartureTestTags.ERROR).assertIsDisplayed()
        composeRule.onNodeWithTag(DepartureTestTags.RETRY).assertDoesNotExist()
    }

    @Test
    fun aStaleLoginOffersReauthenticationWithTheAccountsGoogleProvider() {
        val calls = showDeparture(staleLogin(providers = listOf(AuthProvider.GOOGLE)))

        composeRule.onNodeWithTag(DepartureTestTags.REAUTHENTICATION_TITLE).assertIsDisplayed()
        composeRule
            .onNodeWithTag(DepartureTestTags.REAUTHENTICATION_REASON)
            .assertTextEquals(targetString(R.string.error_auth_requires_recent_login))
        composeRule.onNodeWithTag(DepartureTestTags.FAILURE_TITLE).assertDoesNotExist()
        composeRule.onNodeWithTag(DepartureTestTags.reauthenticate(AuthProvider.GOOGLE)).performClick()

        assertEquals(listOf("reauthenticate:GOOGLE"), calls)
    }

    @Test
    fun aProviderAndroidCannotOpenIsNotOffered() {
        showDeparture(staleLogin(providers = listOf(AuthProvider.APPLE)))

        composeRule.onNodeWithTag(DepartureTestTags.reauthenticate(AuthProvider.APPLE)).assertDoesNotExist()
        composeRule.onNodeWithTag(DepartureTestTags.CANCEL_REAUTHENTICATION).assertIsDisplayed()
    }

    @Test
    fun cancellingReauthenticationInvokesTheCancelAction() {
        val calls = showDeparture(staleLogin(providers = listOf(AuthProvider.GOOGLE)))

        composeRule.onNodeWithTag(DepartureTestTags.CANCEL_REAUTHENTICATION).performClick()

        assertEquals(listOf("cancelReauthentication"), calls)
    }

    private fun showDeparture(
        session: SessionUiState,
        isReauthenticating: Boolean = false,
    ): List<String> {
        val calls = mutableListOf<String>()
        composeRule.setContent {
            DepartureScreen(
                session = session,
                isReauthenticating = isReauthenticating,
                onRetry = { calls += "retry" },
                onDismissMessage = { calls += "dismiss" },
                onReauthenticate = { provider -> calls += "reauthenticate:$provider" },
                onCancelReauthentication = { calls += "cancelReauthentication" },
            )
        }
        return calls
    }

    /**
     * R1: the shared provider refused the credential, so the deletion stays suspended and the host
     * stage stays up. The owner answers it with Google or Cancel, never with a generic OK that would
     * dismiss the only visible trace of a retained deletion.
     */
    @Test
    fun aProviderFailureRetainsTheGoogleAndCancelActions() {
        showDeparture(
            sessionState(
                phase = SessionPhase.PERMANENT,
                message = uiMessage("AUTH.NETWORK_UNAVAILABLE", UiMessageKind.ERROR, null),
            ).copy(providers = listOf(AuthProvider.GOOGLE)),
            isReauthenticating = true,
        )

        listOf(
            DepartureTestTags.REAUTHENTICATION_REASON,
            DepartureTestTags.reauthenticate(AuthProvider.GOOGLE),
            DepartureTestTags.CANCEL_REAUTHENTICATION,
            DepartureTestTags.ERROR,
        ).forEach { tag -> composeRule.onNodeWithTag(tag).assertIsDisplayed() }
        composeRule.onNodeWithTag(DepartureTestTags.RETRY).assertDoesNotExist()
        composeRule.onNodeWithTag(DepartureTestTags.DISMISS).assertDoesNotExist()
    }

    @Test
    fun aRetainedFailureStillOffersGoogle() {
        val calls = showDeparture(providerRejection(), isReauthenticating = true)

        composeRule.onNodeWithTag(DepartureTestTags.reauthenticate(AuthProvider.GOOGLE)).performClick()

        assertEquals(listOf("reauthenticate:GOOGLE"), calls)
    }

    @Test
    fun aRetainedFailureStillOffersCancel() {
        val calls = showDeparture(providerRejection(), isReauthenticating = true)

        composeRule.onNodeWithTag(DepartureTestTags.CANCEL_REAUTHENTICATION).performClick()

        assertEquals(listOf("cancelReauthentication"), calls)
    }

    /** `E4-02`: the departure titles are headings, like every other title of the bundle's screens. */
    @Test
    fun theFailureTitleIsAHeading() {
        showDeparture(failedLocalClear())

        composeRule
            .onNodeWithTag(DepartureTestTags.FAILURE_TITLE)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test
    fun theReauthenticationTitleIsAHeading() {
        showDeparture(staleLogin(listOf(AuthProvider.GOOGLE)))

        composeRule
            .onNodeWithTag(DepartureTestTags.REAUTHENTICATION_TITLE)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    private fun providerRejection(): SessionUiState =
        sessionState(
            phase = SessionPhase.PERMANENT,
            message = uiMessage("AUTH.NETWORK_UNAVAILABLE", UiMessageKind.ERROR, null),
        ).copy(providers = listOf(AuthProvider.GOOGLE))

    private fun failedLocalClear(): SessionUiState =
        sessionState(
            phase = SessionPhase.DELETING,
            message = uiMessage("PERSISTENCE.TRANSACTION_FAILED", UiMessageKind.ERROR, null),
            pendingDepartureRetry = DepartureRetry.LOCAL_CLEAR,
        )

    private fun staleLogin(providers: List<AuthProvider>): SessionUiState =
        sessionState(
            phase = SessionPhase.PERMANENT,
            message = uiMessage("AUTH.REQUIRES_RECENT_LOGIN", UiMessageKind.ERROR, null),
        ).copy(providers = providers)
}
