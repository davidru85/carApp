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

@OptIn(ExperimentalCoroutinesApi::class)
class SessionDepartureTest {
    // ---------------------------------------------------------------- sign-out

    @Test
    fun signOutWithEmptyOutboxSignsOutAndClearsLocalData() =
        runTest {
            val authClient = DepartureAuthClient(AuthState.SignedIn(permanentSession()))
            val departure = RecordingDeparture(pendingCount = 0)
            val holder = holder(authClient, departure)

            holder.requestSignOut()
            advanceUntilIdle()

            assertEquals(1, authClient.signOutCalls)
            assertEquals(1, departure.clearCalls)
            assertEquals(SessionPhase.SIGNED_OUT, holder.state.value.phase)
            assertFalse(holder.state.value.isBusy)
            holder.close()
        }

    @Test
    fun signOutIsRejectedForAnAnonymousSession() =
        runTest {
            val authClient = DepartureAuthClient(AuthState.SignedIn(anonymousSession()))
            val departure = RecordingDeparture(pendingCount = 0)
            val holder = holder(authClient, departure)

            holder.requestSignOut()
            advanceUntilIdle()

            assertEquals(0, authClient.signOutCalls)
            assertEquals(0, departure.clearCalls)
            assertEquals(0, departure.countCalls)
            assertEquals(
                AuthError.ProviderUnavailable.code,
                holder.state.value.message
                    ?.code,
            )
            assertEquals(SessionPhase.ANONYMOUS, holder.state.value.phase)
            holder.close()
        }

    @Test
    fun signOutIsRejectedForALocalOwner() =
        runTest {
            val authClient = DepartureAuthClient(AuthState.SignedOut)
            val departure = RecordingDeparture(pendingCount = 0)
            val holder = localOwnerHolder(authClient, departure)

            holder.requestSignOut()
            advanceUntilIdle()

            assertEquals(0, authClient.signOutCalls)
            assertEquals(0, departure.clearCalls)
            assertEquals(
                AuthError.ProviderUnavailable.code,
                holder.state.value.message
                    ?.code,
            )
            holder.close()
        }

    @Test
    fun signOutWithPendingOutboxPublishesTheExactCountWithoutSigningOut() =
        runTest {
            val authClient = DepartureAuthClient(AuthState.SignedIn(permanentSession()))
            val departure = RecordingDeparture(pendingCount = 7)
            val holder = holder(authClient, departure)

            holder.requestSignOut()
            advanceUntilIdle()

            val state = holder.state.value
            assertEquals(0, authClient.signOutCalls)
            assertEquals(0, departure.clearCalls)
            assertEquals(Confirmation.DiscardPendingChanges, state.message?.confirmation)
            assertEquals("WARNING.PENDING_SYNC", state.message?.code)
            assertEquals(7, state.pendingSyncCount)
            assertEquals(SessionPhase.PERMANENT, state.phase)
            holder.close()
        }

    @Test
    fun confirmingDiscardSignsOutAndClearsLocalData() =
        runTest {
            val authClient = DepartureAuthClient(AuthState.SignedIn(permanentSession()))
            val departure = RecordingDeparture(pendingCount = 2)
            val holder = holder(authClient, departure)

            holder.requestSignOut()
            advanceUntilIdle()
            holder.confirmSignOut(Confirmation.DiscardPendingChanges)
            advanceUntilIdle()

            assertEquals(1, authClient.signOutCalls)
            assertEquals(1, departure.clearCalls)
            assertEquals(SessionPhase.SIGNED_OUT, holder.state.value.phase)
            assertNull(holder.state.value.pendingSyncCount)
            holder.close()
        }

    @Test
    fun discardIsIgnoredWithoutAPrecedingPendingSyncWarning() =
        runTest {
            val authClient = DepartureAuthClient(AuthState.SignedIn(permanentSession()))
            val departure = RecordingDeparture(pendingCount = 0)
            val holder = holder(authClient, departure)

            holder.confirmSignOut(Confirmation.DiscardPendingChanges)
            advanceUntilIdle()

            assertEquals(0, authClient.signOutCalls)
            assertEquals(0, departure.clearCalls)
            assertEquals(SessionPhase.PERMANENT, holder.state.value.phase)
            holder.close()
        }

    @Test
    fun aCountingFailurePreservesThePersistenceErrorAndDoesNotSignOut() =
        runTest {
            val authClient = DepartureAuthClient(AuthState.SignedIn(permanentSession()))
            val departure =
                RecordingDeparture(
                    pendingCount = 0,
                    countResult = Outcome.Err(PersistenceError.DatabaseUnavailable),
                )
            val holder = holder(authClient, departure)

            holder.requestSignOut()
            advanceUntilIdle()

            assertEquals(0, authClient.signOutCalls)
            assertEquals(0, departure.clearCalls)
            assertEquals(
                PersistenceError.DatabaseUnavailable.code,
                holder.state.value.message
                    ?.code,
            )
            holder.close()
        }

    // ----------------------------------------------------------- deletion paths

    @Test
    fun localOwnerDeletionClearsLocalDataOnly() =
        runTest {
            val authClient = DepartureAuthClient(AuthState.SignedOut)
            val departure = RecordingDeparture(pendingCount = 0)
            val holder = localOwnerHolder(authClient, departure)

            holder.requestDeleteAccount()
            advanceUntilIdle()
            holder.confirmDeleteAccount(Confirmation.DeleteLocalData)
            advanceUntilIdle()

            assertEquals(1, departure.clearCalls)
            assertEquals(0, authClient.deleteAccountCalls)
            assertEquals(0, authClient.signOutCalls)
            assertEquals(SessionPhase.SIGNED_OUT, holder.state.value.phase)
            holder.close()
        }

    @Test
    fun anonymousDeletionClearsLocalDataAndEndsTheSessionWithoutTheServerOperation() =
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

            holder.confirmDeleteAccount(Confirmation.DeleteLocalData)
            advanceUntilIdle()

            assertEquals(1, departure.clearCalls)
            assertEquals(0, authClient.deleteAccountCalls)
            // The provider session is ended so the deletion survives a recreated holder.
            assertEquals(1, authClient.signOutCalls)
            assertEquals(AuthState.SignedOut, authClient.authState.value)
            assertEquals(SessionPhase.SIGNED_OUT, holder.state.value.phase)
            holder.close()
        }

    @Test
    fun anonymousDeletionSurvivesRecreatingTheStateHolder() =
        runTest {
            val authClient = DepartureAuthClient(AuthState.SignedIn(anonymousSession()))
            val departure = RecordingDeparture(pendingCount = 0)
            val holder = holder(authClient, departure)

            holder.requestDeleteAccount()
            advanceUntilIdle()
            holder.confirmDeleteAccount(Confirmation.DeleteLocalData)
            advanceUntilIdle()
            holder.close()

            val recreated = holder(authClient, RecordingDeparture(pendingCount = 0))
            advanceUntilIdle()

            assertEquals(SessionPhase.SIGNED_OUT, recreated.state.value.phase)
            recreated.close()
        }

    @Test
    fun permanentDeletionCallsTheServerBeforeClearingLocalData() =
        runTest {
            val log = mutableListOf<String>()
            val authClient = DepartureAuthClient(AuthState.SignedIn(permanentSession()), log = log)
            val departure = RecordingDeparture(pendingCount = 0, log = log)
            val holder = holder(authClient, departure)

            holder.requestDeleteAccount()
            advanceUntilIdle()
            holder.confirmDeleteAccount(Confirmation.DeleteAccount)
            advanceUntilIdle()

            // `D-160` inserts the provider-session cleanup between the two.
            assertEquals(listOf("deleteAccount", "signOut", "clearLocalData"), log)
            assertEquals(SessionPhase.SIGNED_OUT, holder.state.value.phase)
            holder.close()
        }

    @Test
    fun deletionEntersDeletingAndRefusesReentrantIntents() =
        runTest {
            val authClient = DepartureAuthClient(AuthState.SignedIn(permanentSession()))
            val gate = CompletableDeferred<Unit>()
            authClient.deleteGate = gate
            val departure = RecordingDeparture(pendingCount = 0)
            val holder = holder(authClient, departure)

            holder.requestDeleteAccount()
            advanceUntilIdle()
            holder.confirmDeleteAccount(Confirmation.DeleteAccount)
            advanceUntilIdle()

            assertEquals(SessionPhase.DELETING, holder.state.value.phase)
            assertTrue(holder.state.value.isBusy)

            holder.confirmDeleteAccount(Confirmation.DeleteAccount)
            holder.requestSignOut()
            holder.requestDeleteAccount()
            advanceUntilIdle()
            assertEquals(1, authClient.deleteAccountCalls)

            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(1, authClient.deleteAccountCalls)
            assertEquals(1, departure.clearCalls)
            assertEquals(SessionPhase.SIGNED_OUT, holder.state.value.phase)
            holder.close()
        }

    @Test
    fun confirmationWithoutAPendingRequestDoesNothing() =
        runTest {
            val authClient = DepartureAuthClient(AuthState.SignedIn(permanentSession()))
            val departure = RecordingDeparture(pendingCount = 0)
            val holder = holder(authClient, departure)

            holder.confirmDeleteAccount(Confirmation.DeleteAccount)
            advanceUntilIdle()

            assertEquals(0, authClient.deleteAccountCalls)
            assertEquals(0, departure.clearCalls)
            assertEquals(SessionPhase.PERMANENT, holder.state.value.phase)
            holder.close()
        }

    @Test
    fun aSessionChangeBetweenRequestAndConfirmationDiscardsTheRequest() =
        runTest {
            val authClient = DepartureAuthClient(AuthState.SignedIn(permanentSession()))
            val departure = RecordingDeparture(pendingCount = 0)
            val holder = holder(authClient, departure)

            holder.requestDeleteAccount()
            advanceUntilIdle()
            authClient.emit(AuthState.SignedIn(permanentSession(uid = "another-owner")))
            advanceUntilIdle()
            holder.confirmDeleteAccount(Confirmation.DeleteAccount)
            advanceUntilIdle()

            assertEquals(0, authClient.deleteAccountCalls)
            assertEquals(0, departure.clearCalls)
            holder.close()
        }

    @Test
    fun anOwnerKindChangeBetweenRequestAndConfirmationDiscardsTheRequest() =
        runTest {
            val authClient = DepartureAuthClient(AuthState.SignedIn(anonymousSession()))
            val departure = RecordingDeparture(pendingCount = 0)
            val holder = holder(authClient, departure)

            holder.requestDeleteAccount()
            advanceUntilIdle()
            // The anonymous identity was converted before the owner confirmed.
            authClient.emit(AuthState.SignedIn(permanentSession(uid = "anonymous-owner")))
            advanceUntilIdle()
            holder.confirmDeleteAccount(Confirmation.DeleteLocalData)
            advanceUntilIdle()

            assertEquals(0, departure.clearCalls)
            assertEquals(0, authClient.deleteAccountCalls)
            holder.close()
        }

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

            // The D-23 operation does not end the client session, so the flow owes that cleanup.
            assertEquals(listOf("deleteAccount", "signOut", "clearLocalData"), log)
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

private fun anonymousSession(uid: String = "anonymous-owner"): AuthSession =
    AuthSession(uid = uid, isAnonymous = true, providers = setOf(AuthProvider.ANONYMOUS))

private fun permanentSession(uid: String = "permanent-owner"): AuthSession =
    AuthSession(uid = uid, isAnonymous = false, providers = setOf(AuthProvider.GOOGLE))

private fun kotlinx.coroutines.CoroutineScope.holder(
    authClient: DepartureAuthClient,
    departure: RecordingDeparture,
    analyticsTracker: RecordingAnalyticsTracker? = null,
): SessionStateHolder =
    SessionStateHolder(
        scope = this,
        authClient = authClient,
        accountDeparture = departure,
        analyticsTracker = analyticsTracker,
    )

/**
 * A local owner is the published `SessionPhase.LOCAL`, which only a refused anonymous sign-in can
 * produce: there is no Firebase session behind it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
private suspend fun kotlinx.coroutines.test.TestScope.localOwnerHolder(
    authClient: DepartureAuthClient,
    departure: RecordingDeparture,
    analyticsTracker: RecordingAnalyticsTracker? = null,
): SessionStateHolder {
    val holder =
        SessionStateHolder(
            scope = this,
            authClient = authClient,
            accountDeparture = departure,
            analyticsTracker = analyticsTracker,
        )
    holder.startAnonymousSignIn()
    advanceUntilIdle()
    check(holder.state.value.phase == SessionPhase.LOCAL) { "expected a local owner" }
    return holder
}

private class DepartureAuthClient(
    initialState: AuthState,
    var deleteResult: Outcome<Unit, AuthError> = Outcome.Ok(Unit),
    var reauthenticateResult: Outcome<AuthSession, AuthError> = Outcome.Ok(permanentSession()),
    var signOutResult: Outcome<Unit, AuthError> = Outcome.Ok(Unit),
    private val log: MutableList<String> = mutableListOf(),
) : AuthClient {
    private val mutableAuthState = MutableStateFlow(initialState)
    override val authState: StateFlow<AuthState> = mutableAuthState

    var signOutCalls = 0
    var deleteAccountCalls = 0
    var reauthenticateCalls = 0
    var deleteGate: CompletableDeferred<Unit>? = null

    fun emit(state: AuthState) {
        mutableAuthState.value = state
    }

    override suspend fun signInAnonymously(): Outcome<AuthSession, AuthError> =
        Outcome.Err(AuthError.ProviderUnavailable)

    override suspend fun signInWithCredential(
        credential: NativeAuthCredential,
        allowUidChange: Boolean,
    ): Outcome<AuthSession, AuthError> = Outcome.Err(AuthError.ProviderUnavailable)

    override suspend fun linkCredential(credential: NativeAuthCredential): Outcome<AuthSession, AuthError> =
        Outcome.Err(AuthError.ProviderUnavailable)

    override suspend fun reauthenticate(credential: NativeAuthCredential): Outcome<AuthSession, AuthError> {
        reauthenticateCalls += 1
        return reauthenticateResult
    }

    override suspend fun signOut(): Outcome<Unit, AuthError> {
        signOutCalls += 1
        log += "signOut"
        val result = signOutResult
        if (result is Outcome.Ok) mutableAuthState.value = AuthState.SignedOut
        return result
    }

    // Production-faithful: FirebaseAuthClient.deleteAccount() calls the D-23 Admin operation and
    // returns Ok. It does NOT sign out and does NOT publish SignedOut, so the persisted client
    // session outlives it. Emitting SignedOut here would hide the cleanup the flow owes.
    override suspend fun deleteAccount(): Outcome<Unit, AuthError> {
        deleteAccountCalls += 1
        log += "deleteAccount"
        deleteGate?.await()
        return deleteResult
    }
}

private class RecordingDeparture(
    private val pendingCount: Int,
    var countResult: Outcome<Int, AppError>? = null,
    var clearResult: Outcome<Unit, AppError> = Outcome.Ok(Unit),
    private val log: MutableList<String> = mutableListOf(),
) : AccountDepartureHandler {
    var clearCalls = 0
    var countCalls = 0

    override suspend fun pendingOutboxCount(): Outcome<Int, AppError> {
        countCalls += 1
        return countResult ?: Outcome.Ok(pendingCount)
    }

    override suspend fun clearLocalData(): Outcome<Unit, AppError> {
        clearCalls += 1
        log += "clearLocalData"
        return clearResult
    }
}
