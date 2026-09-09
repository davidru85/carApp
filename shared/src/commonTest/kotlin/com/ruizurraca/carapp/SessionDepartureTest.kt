package com.ruizurraca.carapp

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
            holder.confirmDeleteAccount(Confirmation.DeleteAccount)
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
                Confirmation.DeleteAccount,
                holder.state.value.message
                    ?.confirmation,
            )

            holder.confirmDeleteAccount(Confirmation.DeleteAccount)
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
            holder.confirmDeleteAccount(Confirmation.DeleteAccount)
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

            assertEquals(listOf("deleteAccount", "clearLocalData"), log)
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
            holder.confirmDeleteAccount(Confirmation.DeleteAccount)
            advanceUntilIdle()

            assertEquals(0, departure.clearCalls)
            assertEquals(0, authClient.deleteAccountCalls)
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
): SessionStateHolder = SessionStateHolder(scope = this, authClient = authClient, accountDeparture = departure)

/**
 * A local owner is the published `SessionPhase.LOCAL`, which only a refused anonymous sign-in can
 * produce: there is no Firebase session behind it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
private suspend fun kotlinx.coroutines.test.TestScope.localOwnerHolder(
    authClient: DepartureAuthClient,
    departure: RecordingDeparture,
): SessionStateHolder {
    val holder = SessionStateHolder(scope = this, authClient = authClient, accountDeparture = departure)
    holder.startAnonymousSignIn()
    advanceUntilIdle()
    check(holder.state.value.phase == SessionPhase.LOCAL) { "expected a local owner" }
    return holder
}

private class DepartureAuthClient(
    initialState: AuthState,
    var deleteResult: Outcome<Unit, AuthError> = Outcome.Ok(Unit),
    var reauthenticateResult: Outcome<AuthSession, AuthError> = Outcome.Ok(permanentSession()),
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
        mutableAuthState.value = AuthState.SignedOut
        return Outcome.Ok(Unit)
    }

    override suspend fun deleteAccount(): Outcome<Unit, AuthError> {
        deleteAccountCalls += 1
        log += "deleteAccount"
        deleteGate?.await()
        if (deleteResult is Outcome.Ok) mutableAuthState.value = AuthState.SignedOut
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
