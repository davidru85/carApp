package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.auth.AuthClient
import com.ruizurraca.carapp.core.auth.AuthSession
import com.ruizurraca.carapp.core.auth.AuthState
import com.ruizurraca.carapp.core.auth.NativeAuthCredential
import com.ruizurraca.carapp.core.common.AuthError
import com.ruizurraca.carapp.core.common.AuthProvider
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.testing.FakeAuthClient
import com.ruizurraca.carapp.shared.testing.testAppGraphDependencies
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionStateHolderTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun anonymousSignInPublishesTheFirebaseSessionPhase() =
        runTest {
            val session =
                AuthSession(
                    uid = "anonymous-owner",
                    isAnonymous = true,
                    providers = setOf(AuthProvider.ANONYMOUS),
                )
            val authClient = FakeAuthClient(sessionResult = Outcome.Ok(session))
            val dependencies = testAppGraphDependencies(authClient = authClient)
            val graph =
                SwiftAppGraph(
                    graph = DefaultAppGraph(dependencies),
                    dispatchers = dependencies.dispatchers,
                )
            val stateHolder = graph.sessionStateHolder()

            stateHolder.startAnonymousSignIn()
            advanceUntilIdle()

            assertEquals(SessionPhase.ANONYMOUS, stateHolder.state.value.phase)
            assertEquals(listOf(AuthProvider.ANONYMOUS), stateHolder.state.value.providers)
            assertEquals(false, stateHolder.state.value.isBusy)
            assertEquals(null, stateHolder.state.value.message)
            graph.close()
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun authStateRemainsUnknownUntilTheClientResolvesIt() =
        runTest {
            val authClient = RecordingAuthClient(initialState = AuthState.Unknown)
            val stateHolder = SessionStateHolder(scope = this, authClient = authClient)

            assertEquals(SessionPhase.UNKNOWN, stateHolder.state.value.phase)

            authClient.setAuthState(AuthState.SignedOut)
            advanceUntilIdle()

            assertEquals(SessionPhase.SIGNED_OUT, stateHolder.state.value.phase)
            stateHolder.close()
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun offlineAnonymousFailurePublishesLocalSessionAndLeavesRetryAvailable() =
        runTest {
            val authClient =
                RecordingAuthClient(
                    anonymousResult = Outcome.Err(AuthError.NetworkUnavailable),
                )
            val stateHolder = SessionStateHolder(scope = this, authClient = authClient)

            stateHolder.startAnonymousSignIn()
            advanceUntilIdle()

            assertEquals(SessionPhase.LOCAL, stateHolder.state.value.phase)
            assertFalse(stateHolder.state.value.isBusy)
            assertEquals(
                AuthError.NetworkUnavailable.code,
                stateHolder.state.value
                    .message
                    ?.code,
            )

            stateHolder.startAnonymousSignIn()

            assertTrue(stateHolder.state.value.isBusy)
            stateHolder.close()
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun googleCompletionExchangesAnInternalCredentialAndPublishesPermanentSession() =
        runTest {
            val authClient = RecordingAuthClient(credentialResult = Outcome.Ok(permanentSession(AuthProvider.GOOGLE)))
            val stateHolder = SessionStateHolder(scope = this, authClient = authClient)

            stateHolder.startPermanentSignIn(AuthProvider.GOOGLE)

            assertTrue(stateHolder.state.value.isBusy)
            stateHolder.completeGoogleSignIn(idToken = "google-id-token", accessToken = "google-access-token")
            advanceUntilIdle()

            assertEquals(
                NativeAuthCredential.Google("google-id-token", "google-access-token"),
                authClient.credentials.single(),
            )
            assertEquals(SessionPhase.PERMANENT, stateHolder.state.value.phase)
            assertEquals(listOf(AuthProvider.GOOGLE), stateHolder.state.value.providers)
            assertFalse(stateHolder.state.value.isBusy)
            assertFalse(
                stateHolder.state.value
                    .toString()
                    .contains("google-id-token"),
            )
            stateHolder.close()
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun appleCompletionExchangesTheHashedFlowValuesWithoutPublishingTheRawNonce() =
        runTest {
            val authClient = RecordingAuthClient(credentialResult = Outcome.Ok(permanentSession(AuthProvider.APPLE)))
            val stateHolder = SessionStateHolder(scope = this, authClient = authClient)

            stateHolder.startPermanentSignIn(AuthProvider.APPLE)
            stateHolder.completeAppleSignIn(idToken = "apple-id-token", rawNonce = "raw-nonce")
            advanceUntilIdle()

            assertEquals(
                NativeAuthCredential.Apple("apple-id-token", "raw-nonce"),
                authClient.credentials.single(),
            )
            assertEquals(SessionPhase.PERMANENT, stateHolder.state.value.phase)
            assertFalse(
                stateHolder.state.value
                    .toString()
                    .contains("raw-nonce"),
            )
            stateHolder.close()
        }

    @Test
    fun nativeFailuresAreClosedMappedAndEveryAttemptCanRetry() {
        val cases =
            mapOf(
                NativeSignInFailure.CANCELLED to AuthError.Cancelled,
                NativeSignInFailure.NETWORK to AuthError.NetworkUnavailable,
                NativeSignInFailure.CONFIGURATION to AuthError.ProviderUnavailable,
                NativeSignInFailure.UNKNOWN to AuthError.Unknown,
            )
        val stateHolder = SessionStateHolder()

        cases.forEach { (failure, expectedError) ->
            stateHolder.startPermanentSignIn(AuthProvider.GOOGLE)
            assertTrue(stateHolder.state.value.isBusy)

            stateHolder.failSignIn(failure)

            assertFalse(stateHolder.state.value.isBusy)
            assertEquals(
                expectedError.code,
                stateHolder.state.value
                    .message
                    ?.code,
            )
        }

        stateHolder.close()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun mismatchedProviderCompletionDoesNotReachAuthClient() =
        runTest {
            val authClient = RecordingAuthClient()
            val stateHolder = SessionStateHolder(scope = this, authClient = authClient)

            stateHolder.startPermanentSignIn(AuthProvider.APPLE)
            stateHolder.completeGoogleSignIn(idToken = "wrong-provider-token", accessToken = null)
            advanceUntilIdle()

            assertTrue(authClient.credentials.isEmpty())
            assertFalse(stateHolder.state.value.isBusy)
            assertEquals(
                AuthError.ProviderUnavailable.code,
                stateHolder.state.value
                    .message
                    ?.code,
            )
            stateHolder.close()
        }
}

private fun permanentSession(provider: AuthProvider): AuthSession =
    AuthSession(
        uid = "permanent-owner",
        isAnonymous = false,
        providers = setOf(provider),
    )

private class RecordingAuthClient(
    initialState: AuthState = AuthState.SignedOut,
    private var anonymousResult: Outcome<AuthSession, AuthError> = Outcome.Err(AuthError.ProviderUnavailable),
    private var credentialResult: Outcome<AuthSession, AuthError> = Outcome.Err(AuthError.ProviderUnavailable),
) : AuthClient {
    private val mutableAuthState = MutableStateFlow(initialState)
    val credentials = mutableListOf<NativeAuthCredential>()

    override val authState: StateFlow<AuthState> = mutableAuthState

    fun setAuthState(value: AuthState) {
        mutableAuthState.value = value
    }

    override suspend fun signInAnonymously(): Outcome<AuthSession, AuthError> = anonymousResult

    override suspend fun signInWithCredential(
        credential: NativeAuthCredential,
        allowUidChange: Boolean,
    ): Outcome<AuthSession, AuthError> {
        credentials += credential
        return credentialResult
    }

    override suspend fun linkCredential(credential: NativeAuthCredential): Outcome<AuthSession, AuthError> =
        Outcome.Err(AuthError.ProviderUnavailable)

    override suspend fun reauthenticate(credential: NativeAuthCredential): Outcome<AuthSession, AuthError> =
        Outcome.Err(AuthError.ProviderUnavailable)

    override suspend fun signOut(): Outcome<Unit, AuthError> = Outcome.Err(AuthError.ProviderUnavailable)

    override suspend fun deleteAccount(): Outcome<Unit, AuthError> = Outcome.Err(AuthError.ProviderUnavailable)
}
