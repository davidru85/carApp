package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.analytics.AnalyticsEvent
import com.ruizurraca.carapp.core.analytics.ConversionFailureReason
import com.ruizurraca.carapp.core.auth.AuthClient
import com.ruizurraca.carapp.core.auth.AuthSession
import com.ruizurraca.carapp.core.auth.AuthState
import com.ruizurraca.carapp.core.auth.NativeAuthCredential
import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.AuthError
import com.ruizurraca.carapp.core.common.AuthProvider
import com.ruizurraca.carapp.core.common.Confirmation
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.testing.FakeAuthClient
import com.ruizurraca.carapp.core.testing.RecordingAnalyticsTracker
import com.ruizurraca.carapp.shared.testing.testAppGraphDependencies
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
    fun aDeviceWithoutAnAvailableAccountPublishesItsOwnCode() {
        val stateHolder = SessionStateHolder()

        stateHolder.startPermanentSignIn(AuthProvider.GOOGLE)
        stateHolder.failSignIn(NativeSignInFailure.NO_ACCOUNT_AVAILABLE)

        assertFalse(stateHolder.state.value.isBusy)
        assertEquals(
            AuthError.NoAccountAvailable.code,
            stateHolder.state.value
                .message
                ?.code,
            "A device with no account to offer is reported as itself, not as an unclassified failure.",
        )

        stateHolder.close()
    }

    @Test
    fun nativeFailuresAreClosedMappedAndEveryAttemptCanRetry() {
        val cases =
            mapOf(
                NativeSignInFailure.NETWORK to AuthError.NetworkUnavailable,
                NativeSignInFailure.CONFIGURATION to AuthError.ProviderUnavailable,
                NativeSignInFailure.NO_ACCOUNT_AVAILABLE to AuthError.NoAccountAvailable,
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

    @Test
    fun cancellationLeavesARetryableStateWithoutAUserVisibleError() {
        val stateHolder = SessionStateHolder()

        stateHolder.startPermanentSignIn(AuthProvider.GOOGLE)
        stateHolder.failSignIn(NativeSignInFailure.NETWORK)
        assertEquals(
            AuthError.NetworkUnavailable.code,
            stateHolder.state.value
                .message
                ?.code,
        )

        stateHolder.startPermanentSignIn(AuthProvider.GOOGLE)
        assertTrue(stateHolder.state.value.isBusy)

        stateHolder.failSignIn(NativeSignInFailure.CANCELLED)

        assertFalse(stateHolder.state.value.isBusy)
        assertNull(stateHolder.state.value.message)

        stateHolder.startPermanentSignIn(AuthProvider.APPLE)
        assertTrue(stateHolder.state.value.isBusy)
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

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun googleAccountConversionLinksTheCredentialWithoutSigningIntoAnotherUid() =
        runTest {
            val anonymous = anonymousSession()
            val linked = anonymous.copy(isAnonymous = false, providers = setOf(AuthProvider.GOOGLE))
            val authClient =
                RecordingAuthClient(
                    initialState = AuthState.SignedIn(anonymous),
                    linkResult = Outcome.Ok(linked),
                )
            val stateHolder = SessionStateHolder(scope = this, authClient = authClient)

            stateHolder.startAccountConversion(AuthProvider.GOOGLE)
            stateHolder.completeGoogleSignIn("google-id-token", "google-access-token")
            advanceUntilIdle()

            assertEquals(
                listOf<NativeAuthCredential>(
                    NativeAuthCredential.Google("google-id-token", "google-access-token"),
                ),
                authClient.linkedCredentials,
            )
            assertTrue(authClient.credentials.isEmpty(), "normal conversion must not sign into another account")
            assertEquals(anonymous.uid, linked.uid)
            assertEquals(SessionPhase.PERMANENT, stateHolder.state.value.phase)
            assertFalse(stateHolder.state.value.isBusy)
            assertNull(stateHolder.state.value.message)
            stateHolder.close()
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun credentialCollisionPublishesOnlyTheTypedDestructiveConfirmation() =
        runTest {
            val anonymous = anonymousSession()
            val authClient =
                RecordingAuthClient(
                    initialState = AuthState.SignedIn(anonymous),
                    linkResult = Outcome.Err(AuthError.CredentialAlreadyInUse),
                )
            val stateHolder = SessionStateHolder(scope = this, authClient = authClient)

            stateHolder.startAccountConversion(AuthProvider.GOOGLE)
            stateHolder.completeGoogleSignIn("colliding-id-token", null)
            advanceUntilIdle()

            assertEquals(SessionPhase.ANONYMOUS, stateHolder.state.value.phase)
            assertEquals(
                Confirmation.AdoptExistingAccount,
                stateHolder.state.value.message
                    ?.confirmation,
            )
            assertEquals(
                "CONFIRMATION.AdoptExistingAccount",
                stateHolder.state.value.message
                    ?.code,
            )
            assertFalse(stateHolder.state.value.isBusy)
            assertTrue(authClient.credentials.isEmpty(), "a collision cannot switch sessions before confirmation")
            stateHolder.close()
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun cancellingTheCollisionLeavesTheAnonymousSessionAndCredentialUnused() =
        runTest {
            val anonymous = anonymousSession()
            val authClient =
                RecordingAuthClient(
                    initialState = AuthState.SignedIn(anonymous),
                    linkResult = Outcome.Err(AuthError.CredentialAlreadyInUse),
                )
            val conversion = RecordingAccountConversion()
            val stateHolder =
                SessionStateHolder(
                    scope = this,
                    authClient = authClient,
                    accountConversion = conversion,
                )

            stateHolder.startAccountConversion(AuthProvider.GOOGLE)
            stateHolder.completeGoogleSignIn("colliding-id-token", null)
            advanceUntilIdle()
            stateHolder.clearMessage()
            stateHolder.confirmAccountConversion(Confirmation.AdoptExistingAccount)
            advanceUntilIdle()

            assertEquals(AuthState.SignedIn(anonymous), authClient.authState.value)
            assertTrue(authClient.credentials.isEmpty())
            assertTrue(conversion.calls.isEmpty(), "dismissal destroys the ephemeral collision credential")
            assertNull(stateHolder.state.value.message)
            stateHolder.close()
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun confirmationDelegatesTheCollisionWithTheAnonymousUidAndEphemeralCredential() =
        runTest {
            val anonymous = anonymousSession()
            val permanent = permanentSession(AuthProvider.GOOGLE)
            val credential = NativeAuthCredential.Google("colliding-id-token", null)
            val authClient =
                RecordingAuthClient(
                    initialState = AuthState.SignedIn(anonymous),
                    linkResult = Outcome.Err(AuthError.CredentialAlreadyInUse),
                )
            val conversion = RecordingAccountConversion(Outcome.Ok(permanent))
            val stateHolder =
                SessionStateHolder(
                    scope = this,
                    authClient = authClient,
                    accountConversion = conversion,
                )

            stateHolder.startAccountConversion(AuthProvider.GOOGLE)
            stateHolder.completeGoogleSignIn(credential.idToken, credential.accessToken)
            advanceUntilIdle()
            stateHolder.confirmAccountConversion(Confirmation.AdoptExistingAccount)
            advanceUntilIdle()

            assertEquals(
                listOf<Pair<String, NativeAuthCredential>>(anonymous.uid to credential),
                conversion.calls,
            )
            assertEquals(SessionPhase.PERMANENT, stateHolder.state.value.phase)
            assertFalse(stateHolder.state.value.isBusy)
            stateHolder.close()
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun aDifferentConfirmationCannotStartTheDestructiveCollisionFlow() =
        runTest {
            val anonymous = anonymousSession()
            val authClient =
                RecordingAuthClient(
                    initialState = AuthState.SignedIn(anonymous),
                    linkResult = Outcome.Err(AuthError.CredentialAlreadyInUse),
                )
            val conversion = RecordingAccountConversion()
            val stateHolder =
                SessionStateHolder(
                    scope = this,
                    authClient = authClient,
                    accountConversion = conversion,
                )

            stateHolder.startAccountConversion(AuthProvider.GOOGLE)
            stateHolder.completeGoogleSignIn("colliding-id-token", null)
            advanceUntilIdle()
            stateHolder.confirmAccountConversion(Confirmation.DeleteAccount)
            advanceUntilIdle()

            assertTrue(conversion.calls.isEmpty())
            assertEquals(
                Confirmation.AdoptExistingAccount,
                stateHolder.state.value.message
                    ?.confirmation,
            )
            assertEquals(SessionPhase.ANONYMOUS, stateHolder.state.value.phase)
            stateHolder.close()
        }

    @Test
    fun accountConversionStartTracksTheClosedStartedEvent() =
        runTest {
            val tracker = RecordingAnalyticsTracker(initiallyEnabled = true)
            val authClient = RecordingAuthClient(initialState = AuthState.SignedIn(anonymousSession()))
            val dependencies = testAppGraphDependencies(authClient = authClient, analyticsTracker = tracker)
            val graph = SwiftAppGraph(DefaultAppGraph(dependencies), dependencies.dispatchers)
            val stateHolder = graph.sessionStateHolder()

            stateHolder.startAccountConversion(AuthProvider.GOOGLE)

            assertEquals(listOf(AnalyticsEvent.AccountConversionStarted), tracker.events)
            graph.close()
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun successfulAccountLinkTracksTheClosedCompletedEvent() =
        runTest {
            val tracker = RecordingAnalyticsTracker(initiallyEnabled = true)
            val anonymous = anonymousSession()
            val linked = anonymous.copy(isAnonymous = false, providers = setOf(AuthProvider.GOOGLE))
            val authClient =
                RecordingAuthClient(
                    initialState = AuthState.SignedIn(anonymous),
                    linkResult = Outcome.Ok(linked),
                )
            val dependencies = testAppGraphDependencies(authClient = authClient, analyticsTracker = tracker)
            val graph = SwiftAppGraph(DefaultAppGraph(dependencies), dependencies.dispatchers)
            val stateHolder = graph.sessionStateHolder()

            stateHolder.startAccountConversion(AuthProvider.GOOGLE)
            stateHolder.completeGoogleSignIn("id-token", null)
            advanceUntilIdle()

            assertEquals(AnalyticsEvent.AccountConversionCompleted, tracker.events.last())
            graph.close()
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun dismissingACollisionTracksTheClosedCancelledFailure() =
        runTest {
            val tracker = RecordingAnalyticsTracker(initiallyEnabled = true)
            val authClient =
                RecordingAuthClient(
                    initialState = AuthState.SignedIn(anonymousSession()),
                    linkResult = Outcome.Err(AuthError.CredentialAlreadyInUse),
                )
            val dependencies = testAppGraphDependencies(authClient = authClient, analyticsTracker = tracker)
            val graph = SwiftAppGraph(DefaultAppGraph(dependencies), dependencies.dispatchers)
            val stateHolder = graph.sessionStateHolder()

            stateHolder.startAccountConversion(AuthProvider.GOOGLE)
            stateHolder.completeGoogleSignIn("colliding-id-token", null)
            advanceUntilIdle()
            stateHolder.clearMessage()

            assertEquals(
                AnalyticsEvent.AccountConversionFailed(ConversionFailureReason.CANCELLED),
                tracker.events.last(),
            )
            graph.close()
        }
}

private fun anonymousSession(): AuthSession =
    AuthSession(
        uid = "anonymous-owner",
        isAnonymous = true,
        providers = setOf(AuthProvider.ANONYMOUS),
    )

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
    private var linkResult: Outcome<AuthSession, AuthError> = Outcome.Err(AuthError.ProviderUnavailable),
) : AuthClient {
    private val mutableAuthState = MutableStateFlow(initialState)
    val credentials = mutableListOf<NativeAuthCredential>()
    val linkedCredentials = mutableListOf<NativeAuthCredential>()

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
        linkResult.also { linkedCredentials += credential }

    override suspend fun reauthenticate(credential: NativeAuthCredential): Outcome<AuthSession, AuthError> =
        Outcome.Err(AuthError.ProviderUnavailable)

    override suspend fun signOut(): Outcome<Unit, AuthError> = Outcome.Err(AuthError.ProviderUnavailable)

    override suspend fun deleteAccount(): Outcome<Unit, AuthError> = Outcome.Err(AuthError.ProviderUnavailable)
}

private class RecordingAccountConversion(
    private val result: Outcome<AuthSession, AppError> = Outcome.Err(AuthError.ProviderUnavailable),
) : AccountConversionHandler {
    val calls = mutableListOf<Pair<String, NativeAuthCredential>>()

    override suspend fun confirm(
        anonymousUid: String,
        credential: NativeAuthCredential,
    ): Outcome<AuthSession, AppError> = result.also { calls += anonymousUid to credential }
}
