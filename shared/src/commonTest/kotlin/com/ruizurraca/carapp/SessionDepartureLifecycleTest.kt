package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.analytics.AnalyticsEvent
import com.ruizurraca.carapp.core.auth.AuthClient
import com.ruizurraca.carapp.core.auth.AuthSession
import com.ruizurraca.carapp.core.auth.AuthState
import com.ruizurraca.carapp.core.auth.NativeAuthCredential
import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.AuthError
import com.ruizurraca.carapp.core.common.AuthProvider
import com.ruizurraca.carapp.core.common.Confirmation
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.PersistenceError
import com.ruizurraca.carapp.core.testing.RecordingAnalyticsTracker
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The departure lifecycle corrections of the second owner review: the provider session after a
 * D-23 deletion, the retryable local work, the confirmation protocol and the analytics scope.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionDepartureLifecycleTest {
    // ------------------------------------------- provider session after deletion

    @Test
    fun permanentDeletionEndsTheProviderSessionBeforeClearingLocalData() =
        runTest {
            val log = mutableListOf<String>()
            val authClient = DepartureAuthClient(AuthState.SignedIn(permanentSession()), log = log)
            val departure = RecordingDeparture(pendingCount = 0, log = log)
            val holder = holder(authClient, departure)

            holder.requestDeleteAccount()
            advanceUntilIdle()
            holder.confirmDeleteAccount(Confirmation.DeleteAccount)
            advanceUntilIdle()

            // The durable marker writes share this log; this assertion is about the destructive
            // steps and their order. `AccountDepartureRecoveryTest` pins the marker writes.
            val destructive = log.filter { it == "deleteAccount" || it == "signOut" || it == "clearLocalData" }
            // The D-23 operation does not end the client session, so the flow owes that cleanup.
            assertEquals(listOf("deleteAccount", "signOut", "clearLocalData"), destructive)
            assertEquals(AuthState.SignedOut, authClient.authState.value)
            assertEquals(SessionPhase.SIGNED_OUT, holder.state.value.phase)
            holder.close()
        }

    @Test
    fun permanentDeletionSurvivesRecreatingTheStateHolder() =
        runTest {
            val authClient = DepartureAuthClient(AuthState.SignedIn(permanentSession()))
            val departure = RecordingDeparture(pendingCount = 0)
            val holder = holder(authClient, departure)

            holder.requestDeleteAccount()
            advanceUntilIdle()
            holder.confirmDeleteAccount(Confirmation.DeleteAccount)
            advanceUntilIdle()
            holder.close()

            val recreated = holder(authClient, RecordingDeparture(pendingCount = 0))
            advanceUntilIdle()

            assertFalse(recreated.state.value.phase == SessionPhase.PERMANENT)
            assertEquals(SessionPhase.SIGNED_OUT, recreated.state.value.phase)
            recreated.close()
        }

    @Test
    fun aProviderSignOutFailureAfterRemoteDeletionNeverRepeatsTheServerOperation() =
        runTest {
            val authClient =
                DepartureAuthClient(
                    AuthState.SignedIn(permanentSession()),
                    signOutResult = Outcome.Err(AuthError.NetworkUnavailable),
                )
            val departure = RecordingDeparture(pendingCount = 0)
            val holder = holder(authClient, departure)

            holder.requestDeleteAccount()
            advanceUntilIdle()
            holder.confirmDeleteAccount(Confirmation.DeleteAccount)
            advanceUntilIdle()

            assertEquals(1, authClient.deleteAccountCalls)
            assertEquals(0, departure.clearCalls)
            assertFalse(holder.state.value.phase == SessionPhase.SIGNED_OUT)

            authClient.signOutResult = Outcome.Ok(Unit)
            holder.retryDeparture()
            advanceUntilIdle()

            assertEquals(1, authClient.deleteAccountCalls)
            assertEquals(1, departure.clearCalls)
            assertEquals(SessionPhase.SIGNED_OUT, holder.state.value.phase)
            holder.close()
        }

    // --------------------------------------------------------- retryable clear

    @Test
    fun aLocalClearFailureOffersATypedRetry() =
        runTest {
            val authClient = DepartureAuthClient(AuthState.SignedIn(permanentSession()))
            val departure =
                RecordingDeparture(
                    pendingCount = 0,
                    clearResult = Outcome.Err(PersistenceError.TransactionFailed),
                )
            val holder = holder(authClient, departure)

            holder.requestDeleteAccount()
            advanceUntilIdle()
            holder.confirmDeleteAccount(Confirmation.DeleteAccount)
            advanceUntilIdle()

            assertEquals(DepartureRetry.LOCAL_CLEAR, holder.state.value.pendingDepartureRetry)
            holder.close()
        }

    @Test
    fun retryDepartureRepeatsOnlyTheLocalClearAfterASignOut() =
        runTest {
            val authClient = DepartureAuthClient(AuthState.SignedIn(permanentSession()))
            val departure =
                RecordingDeparture(
                    pendingCount = 0,
                    clearResult = Outcome.Err(PersistenceError.TransactionFailed),
                )
            val holder = holder(authClient, departure)

            holder.requestSignOut()
            advanceUntilIdle()
            assertEquals(1, authClient.signOutCalls)
            assertEquals(DepartureRetry.LOCAL_CLEAR, holder.state.value.pendingDepartureRetry)

            departure.clearResult = Outcome.Ok(Unit)
            holder.retryDeparture()
            advanceUntilIdle()

            // The provider sign-out already succeeded and MUST NOT be repeated.
            assertEquals(1, authClient.signOutCalls)
            assertEquals(2, departure.clearCalls)
            assertEquals(SessionPhase.SIGNED_OUT, holder.state.value.phase)
            assertNull(holder.state.value.pendingDepartureRetry)
            holder.close()
        }

    @Test
    fun retryDepartureRepeatsTheLocalClearForALocalOwner() =
        runTest {
            val authClient = DepartureAuthClient(AuthState.SignedOut)
            val departure =
                RecordingDeparture(
                    pendingCount = 0,
                    clearResult = Outcome.Err(PersistenceError.TransactionFailed),
                )
            val holder = localOwnerHolder(authClient, departure)

            holder.requestDeleteAccount()
            advanceUntilIdle()
            holder.confirmDeleteAccount(Confirmation.DeleteLocalData)
            advanceUntilIdle()
            assertEquals(DepartureRetry.LOCAL_CLEAR, holder.state.value.pendingDepartureRetry)

            departure.clearResult = Outcome.Ok(Unit)
            holder.retryDeparture()
            advanceUntilIdle()

            assertEquals(2, departure.clearCalls)
            assertEquals(0, authClient.deleteAccountCalls)
            assertEquals(SessionPhase.SIGNED_OUT, holder.state.value.phase)
            holder.close()
        }

    @Test
    fun retryDepartureNeverRepeatsAServerDeletionThatSucceeded() =
        runTest {
            val authClient = DepartureAuthClient(AuthState.SignedIn(permanentSession()))
            val departure =
                RecordingDeparture(
                    pendingCount = 0,
                    clearResult = Outcome.Err(PersistenceError.TransactionFailed),
                )
            val holder = holder(authClient, departure)

            holder.requestDeleteAccount()
            advanceUntilIdle()
            holder.confirmDeleteAccount(Confirmation.DeleteAccount)
            advanceUntilIdle()

            departure.clearResult = Outcome.Ok(Unit)
            holder.retryDeparture()
            advanceUntilIdle()

            assertEquals(1, authClient.deleteAccountCalls)
            assertEquals(2, departure.clearCalls)
            assertEquals(SessionPhase.SIGNED_OUT, holder.state.value.phase)
            holder.close()
        }

    @Test
    fun retryDepartureDoesNothingWithoutRetainedWork() =
        runTest {
            val authClient = DepartureAuthClient(AuthState.SignedIn(permanentSession()))
            val departure = RecordingDeparture(pendingCount = 0)
            val holder = holder(authClient, departure)

            holder.retryDeparture()
            advanceUntilIdle()

            assertEquals(0, departure.clearCalls)
            assertEquals(0, authClient.deleteAccountCalls)
            assertNull(holder.state.value.pendingDepartureRetry)
            holder.close()
        }

    // ------------------------------------------------ confirmation semantics

    @Test
    fun localOwnerDeletionAsksForTheLocalDataConfirmation() =
        runTest {
            val authClient = DepartureAuthClient(AuthState.SignedOut)
            val departure = RecordingDeparture(pendingCount = 0)
            val holder = localOwnerHolder(authClient, departure)

            holder.requestDeleteAccount()
            advanceUntilIdle()

            // §20.2 reserves DeleteAccount for actual account deletion.
            assertEquals(
                Confirmation.DeleteLocalData,
                holder.state.value.message
                    ?.confirmation,
            )
            assertEquals(
                "CONFIRMATION.DeleteLocalData",
                holder.state.value.message
                    ?.code,
            )
            holder.close()
        }

    @Test
    fun anonymousDeletionAsksForTheLocalDataConfirmation() =
        runTest {
            val authClient = DepartureAuthClient(AuthState.SignedIn(anonymousSession()))
            val departure = RecordingDeparture(pendingCount = 0)
            val holder = holder(authClient, departure)

            holder.requestDeleteAccount()
            advanceUntilIdle()

            assertEquals(
                Confirmation.DeleteLocalData,
                holder.state.value.message
                    ?.confirmation,
            )
            assertEquals(
                "CONFIRMATION.DeleteLocalData",
                holder.state.value.message
                    ?.code,
            )
            holder.close()
        }

    @Test
    fun localDataDeletionWithPendingOutboxDiscardsFirstThenConfirmsDestructively() =
        runTest {
            val authClient = DepartureAuthClient(AuthState.SignedIn(anonymousSession()))
            val departure = RecordingDeparture(pendingCount = 4)
            val holder = holder(authClient, departure)

            holder.requestDeleteAccount()
            advanceUntilIdle()

            // §20.2 assigns DiscardPendingChanges to local-data deletion with pending outbox rows.
            assertEquals(
                Confirmation.DiscardPendingChanges,
                holder.state.value.message
                    ?.confirmation,
            )
            assertEquals(4, holder.state.value.pendingSyncCount)
            assertEquals(0, departure.clearCalls)

            holder.confirmDeleteAccount(Confirmation.DiscardPendingChanges)
            advanceUntilIdle()

            assertEquals(
                Confirmation.DeleteLocalData,
                holder.state.value.message
                    ?.confirmation,
            )
            assertEquals(0, departure.clearCalls)

            holder.confirmDeleteAccount(Confirmation.DeleteLocalData)
            advanceUntilIdle()

            assertEquals(1, departure.clearCalls)
            assertEquals(SessionPhase.SIGNED_OUT, holder.state.value.phase)
            holder.close()
        }

    @Test
    fun theDestructiveLocalDataConfirmationIsNotAcceptedBeforeTheDiscard() =
        runTest {
            val authClient = DepartureAuthClient(AuthState.SignedIn(anonymousSession()))
            val departure = RecordingDeparture(pendingCount = 4)
            val holder = holder(authClient, departure)

            holder.requestDeleteAccount()
            advanceUntilIdle()
            holder.confirmDeleteAccount(Confirmation.DeleteLocalData)
            advanceUntilIdle()

            assertEquals(0, departure.clearCalls)
            holder.close()
        }

    // ------------------------------------------------------------- analytics

    @Test
    fun permanentDeletionEmitsTheAccountDeletionLifecycle() =
        runTest {
            val tracker = RecordingAnalyticsTracker(initiallyEnabled = true)
            val authClient = DepartureAuthClient(AuthState.SignedIn(permanentSession()))
            val departure = RecordingDeparture(pendingCount = 0)
            val holder = holder(authClient, departure, tracker)

            holder.requestDeleteAccount()
            advanceUntilIdle()
            holder.confirmDeleteAccount(Confirmation.DeleteAccount)
            advanceUntilIdle()

            assertEquals(
                listOf(AnalyticsEvent.AccountDeletionStarted, AnalyticsEvent.AccountDeletionCompleted),
                tracker.events,
            )
            holder.close()
        }

    @Test
    fun localOwnerDeletionEmitsNoAccountDeletionAnalytics() =
        runTest {
            val tracker = RecordingAnalyticsTracker(initiallyEnabled = true)
            val authClient = DepartureAuthClient(AuthState.SignedOut)
            val departure = RecordingDeparture(pendingCount = 0)
            val holder = localOwnerHolder(authClient, departure, tracker)

            holder.requestDeleteAccount()
            advanceUntilIdle()
            holder.confirmDeleteAccount(Confirmation.DeleteLocalData)
            advanceUntilIdle()

            assertEquals(1, departure.clearCalls)
            // Clearing local data is not account deletion, so it reports none of its events.
            assertTrue(tracker.events.none { it is AnalyticsEvent.AccountDeletionCompleted })
            assertTrue(tracker.events.none { it is AnalyticsEvent.AccountDeletionStarted })
            holder.close()
        }

    @Test
    fun anonymousDeletionEmitsNoAccountDeletionAnalytics() =
        runTest {
            val tracker = RecordingAnalyticsTracker(initiallyEnabled = true)
            val authClient = DepartureAuthClient(AuthState.SignedIn(anonymousSession()))
            val departure = RecordingDeparture(pendingCount = 0)
            val holder = holder(authClient, departure, tracker)

            holder.requestDeleteAccount()
            advanceUntilIdle()
            holder.confirmDeleteAccount(Confirmation.DeleteLocalData)
            advanceUntilIdle()

            assertEquals(1, departure.clearCalls)
            assertTrue(tracker.events.none { it is AnalyticsEvent.AccountDeletionCompleted })
            assertTrue(tracker.events.none { it is AnalyticsEvent.AccountDeletionStarted })
            holder.close()
        }

    // -------------------------------------------------------------- failures

    @Test
    fun serverDeletionFailurePreservesLocalDataAndReportsTheError() =
        runTest {
            val authClient =
                DepartureAuthClient(
                    AuthState.SignedIn(permanentSession()),
                    deleteResult = Outcome.Err(AuthError.AccountDeletionRemoteFailed),
                )
            val departure = RecordingDeparture(pendingCount = 0)
            val holder = holder(authClient, departure)

            holder.requestDeleteAccount()
            advanceUntilIdle()
            holder.confirmDeleteAccount(Confirmation.DeleteAccount)
            advanceUntilIdle()

            assertEquals(0, departure.clearCalls)
            assertEquals(
                AuthError.AccountDeletionRemoteFailed.code,
                holder.state.value.message
                    ?.code,
            )
            assertEquals(SessionPhase.PERMANENT, holder.state.value.phase)
            holder.close()
        }

    @Test
    fun aLocalClearFailureAfterSignOutNeverPublishesSignedOut() =
        runTest {
            val authClient = DepartureAuthClient(AuthState.SignedIn(permanentSession()))
            val departure =
                RecordingDeparture(
                    pendingCount = 0,
                    clearResult = Outcome.Err(PersistenceError.TransactionFailed),
                )
            val holder = holder(authClient, departure)

            holder.requestSignOut()
            advanceUntilIdle()

            assertEquals(1, authClient.signOutCalls)
            assertEquals(1, departure.clearCalls)
            assertEquals(
                PersistenceError.TransactionFailed.code,
                holder.state.value.message
                    ?.code,
            )
            assertFalse(holder.state.value.phase == SessionPhase.SIGNED_OUT)
            holder.close()
        }

    @Test
    fun aLocalClearFailureAfterRemoteDeletionReportsNoCompletion() =
        runTest {
            val authClient = DepartureAuthClient(AuthState.SignedIn(permanentSession()))
            val departure =
                RecordingDeparture(
                    pendingCount = 0,
                    clearResult = Outcome.Err(PersistenceError.TransactionFailed),
                )
            val holder = holder(authClient, departure)

            holder.requestDeleteAccount()
            advanceUntilIdle()
            holder.confirmDeleteAccount(Confirmation.DeleteAccount)
            advanceUntilIdle()

            assertEquals(1, authClient.deleteAccountCalls)
            assertEquals(
                PersistenceError.TransactionFailed.code,
                holder.state.value.message
                    ?.code,
            )
            assertFalse(holder.state.value.phase == SessionPhase.SIGNED_OUT)
            holder.close()
        }

    @Test
    fun retryingAfterRemoteSuccessRepeatsOnlyTheLocalClear() =
        runTest {
            val authClient = DepartureAuthClient(AuthState.SignedIn(permanentSession()))
            val departure =
                RecordingDeparture(
                    pendingCount = 0,
                    clearResult = Outcome.Err(PersistenceError.TransactionFailed),
                )
            val holder = holder(authClient, departure)

            holder.requestDeleteAccount()
            advanceUntilIdle()
            holder.confirmDeleteAccount(Confirmation.DeleteAccount)
            advanceUntilIdle()
            assertEquals(1, authClient.deleteAccountCalls)

            departure.clearResult = Outcome.Ok(Unit)
            holder.confirmDeleteAccount(Confirmation.DeleteAccount)
            advanceUntilIdle()

            assertEquals(1, authClient.deleteAccountCalls)
            assertEquals(2, departure.clearCalls)
            assertEquals(SessionPhase.SIGNED_OUT, holder.state.value.phase)
            holder.close()
        }

    // ------------------------------------------------------- recent-login flow

    @Test
    fun aStaleLoginKeepsTheRequestAndResumesAfterReauthentication() =
        runTest {
            val authClient =
                DepartureAuthClient(
                    AuthState.SignedIn(permanentSession()),
                    deleteResult = Outcome.Err(AuthError.RequiresRecentLogin),
                )
            val departure = RecordingDeparture(pendingCount = 0)
            val holder = holder(authClient, departure)

            holder.requestDeleteAccount()
            advanceUntilIdle()
            holder.confirmDeleteAccount(Confirmation.DeleteAccount)
            advanceUntilIdle()

            assertEquals(
                AuthError.RequiresRecentLogin.code,
                holder.state.value.message
                    ?.code,
            )
            assertEquals(0, departure.clearCalls)

            authClient.deleteResult = Outcome.Ok(Unit)
            holder.startReauthentication(AuthProvider.GOOGLE)
            holder.completeGoogleSignIn("fresh-id-token", null)
            advanceUntilIdle()

            assertEquals(1, authClient.reauthenticateCalls)
            assertEquals(2, authClient.deleteAccountCalls)
            assertEquals(1, departure.clearCalls)
            assertEquals(SessionPhase.SIGNED_OUT, holder.state.value.phase)
            holder.close()
        }

    @Test
    fun aFailedReauthenticationKeepsTheRequestAndPreservesLocalData() =
        runTest {
            val authClient =
                DepartureAuthClient(
                    AuthState.SignedIn(permanentSession()),
                    deleteResult = Outcome.Err(AuthError.RequiresRecentLogin),
                    reauthenticateResult = Outcome.Err(AuthError.NetworkUnavailable),
                )
            val departure = RecordingDeparture(pendingCount = 0)
            val holder = holder(authClient, departure)

            holder.requestDeleteAccount()
            advanceUntilIdle()
            holder.confirmDeleteAccount(Confirmation.DeleteAccount)
            advanceUntilIdle()
            holder.startReauthentication(AuthProvider.GOOGLE)
            holder.completeGoogleSignIn("stale-id-token", null)
            advanceUntilIdle()

            assertEquals(1, authClient.reauthenticateCalls)
            assertEquals(1, authClient.deleteAccountCalls)
            assertEquals(0, departure.clearCalls)
            assertEquals(
                AuthError.NetworkUnavailable.code,
                holder.state.value.message
                    ?.code,
            )
            holder.close()
        }

    @Test
    fun cancellingReauthenticationAbandonsTheDeletionWithoutClearingLocalData() =
        runTest {
            val authClient =
                DepartureAuthClient(
                    AuthState.SignedIn(permanentSession()),
                    deleteResult = Outcome.Err(AuthError.RequiresRecentLogin),
                )
            val departure = RecordingDeparture(pendingCount = 0)
            val holder = holder(authClient, departure)

            holder.requestDeleteAccount()
            advanceUntilIdle()
            holder.confirmDeleteAccount(Confirmation.DeleteAccount)
            advanceUntilIdle()
            holder.startReauthentication(AuthProvider.GOOGLE)
            holder.failSignIn(NativeSignInFailure.CANCELLED)
            advanceUntilIdle()

            assertEquals(0, authClient.reauthenticateCalls)
            assertEquals(0, departure.clearCalls)

            // The abandoned request no longer authorises a confirmation.
            holder.confirmDeleteAccount(Confirmation.DeleteAccount)
            advanceUntilIdle()
            assertEquals(1, authClient.deleteAccountCalls)
            assertEquals(0, departure.clearCalls)
            holder.close()
        }

    @Test
    fun reauthenticationIsRejectedWithoutAStaleDeletionRequest() =
        runTest {
            val authClient = DepartureAuthClient(AuthState.SignedIn(permanentSession()))
            val departure = RecordingDeparture(pendingCount = 0)
            val holder = holder(authClient, departure)

            holder.startReauthentication(AuthProvider.GOOGLE)
            holder.completeGoogleSignIn("fresh-id-token", null)
            advanceUntilIdle()

            assertEquals(0, authClient.reauthenticateCalls)
            holder.close()
        }
}
