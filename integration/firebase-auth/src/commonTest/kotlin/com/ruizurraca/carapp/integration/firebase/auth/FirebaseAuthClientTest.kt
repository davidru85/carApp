package com.ruizurraca.carapp.integration.firebase.auth

import com.ruizurraca.carapp.core.auth.AuthSession
import com.ruizurraca.carapp.core.auth.AuthState
import com.ruizurraca.carapp.core.auth.AuthToken
import com.ruizurraca.carapp.core.auth.NativeAuthCredential
import com.ruizurraca.carapp.core.common.AppClock
import com.ruizurraca.carapp.core.common.AuthError
import com.ruizurraca.carapp.core.common.AuthProvider
import com.ruizurraca.carapp.core.common.FRESH_LOGIN_THRESHOLD_MS
import com.ruizurraca.carapp.core.common.Outcome
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class FirebaseAuthClientTest {
    @Test
    fun retainedAnonymousUserIsAvailableWhenTheClientIsCreated() =
        runTest {
            val gateway = FakeFirebaseAuthGateway(currentUser = anonymousUser("retained-uid"))

            val client = FirebaseAuthClient(gateway = gateway, coroutineScope = backgroundScope)

            assertEquals(
                AuthState.SignedIn(
                    AuthSession(
                        uid = "retained-uid",
                        isAnonymous = true,
                        providers = setOf(AuthProvider.ANONYMOUS),
                    ),
                ),
                client.authState.value,
            )
        }

    @Test
    fun authStateStartsAtUnknownBeforeInitialGatewayEmission() =
        runTest {
            val gateway = FakeFirebaseAuthGateway(autoEmitAuthState = false)
            val client =
                FirebaseAuthClient(
                    gateway = gateway,
                    clock =
                        AppClock {
                            Instant.fromEpochMilliseconds(1_000L)
                        },
                    coroutineScope = backgroundScope,
                )

            assertEquals(AuthState.Unknown, client.authState.value)

            gateway.emitAuthState(anonymousUser("newly-emitted-user"))
            runCurrent()
            val session = assertIs<AuthState.SignedIn>(client.authState.value).session
            assertEquals("newly-emitted-user", session.uid)
        }

    @Test
    fun authStateObservesExternalTransitionsFromGateway() =
        runTest {
            val user = anonymousUser("retained-user")
            val gateway = FakeFirebaseAuthGateway(currentUser = user)
            val client =
                FirebaseAuthClient(
                    gateway = gateway,
                    clock =
                        AppClock {
                            Instant.fromEpochMilliseconds(1_000L)
                        },
                    coroutineScope = backgroundScope,
                )

            assertEquals(AuthState.SignedIn(user.toSession()), client.authState.value)

            // External event: session cleaned up or deleted remotely
            gateway.emitAuthState(null)
            runCurrent()
            assertEquals(AuthState.SignedOut, client.authState.value)
        }

    @Test
    fun anonymousSignInReturnsAndPublishesTheFirebaseSession() =
        runTest {
            val gateway =
                FakeFirebaseAuthGateway(
                    currentUser = null,
                    signedInUser = anonymousUser("new-uid"),
                )
            val client = FirebaseAuthClient(gateway = gateway, coroutineScope = backgroundScope)

            val result = client.signInAnonymously()

            val session = assertIs<Outcome.Ok<AuthSession>>(result).value
            assertEquals("new-uid", session.uid)
            assertEquals(true, session.isAnonymous)
            assertEquals(setOf(AuthProvider.ANONYMOUS), session.providers)
            assertEquals(AuthState.SignedIn(session), client.authState.value)
        }

    @Test
    fun anonymousSignInRetainsCreationTimestampFromMetadata() =
        runTest {
            val createdAt = Instant.fromEpochMilliseconds(1_700_000_000_000L)
            val gateway =
                FakeFirebaseAuthGateway(
                    currentUser = null,
                    signedInUser = anonymousUser("meta-uid", createdAt = createdAt),
                )
            val client = FirebaseAuthClient(gateway = gateway, coroutineScope = backgroundScope)

            val result = client.signInAnonymously()

            val session = assertIs<Outcome.Ok<AuthSession>>(result).value
            assertEquals(createdAt, session.createdAt)
        }

    @Test
    fun googleSignInExchangesNativeCredentialAndUpdatesSession() =
        runTest {
            val googleUser =
                FirebaseAuthUser(
                    uid = "google-uid",
                    isAnonymous = false,
                    providerIds = setOf("google.com"),
                )
            val gateway = FakeFirebaseAuthGateway(currentUser = null, googleUser = googleUser)
            val client = FirebaseAuthClient(gateway = gateway, coroutineScope = backgroundScope)

            val result =
                client.signInWithCredential(
                    NativeAuthCredential.Google(idToken = "google-id-token", accessToken = "google-access-token"),
                )

            val session = assertIs<Outcome.Ok<AuthSession>>(result).value
            assertEquals("google-uid", session.uid)
            assertEquals(false, session.isAnonymous)
            assertEquals(setOf(AuthProvider.GOOGLE), session.providers)
            assertEquals(AuthState.SignedIn(session), client.authState.value)
        }

    @Test
    fun appleSignInExchangesNativeCredentialAndUpdatesSession() =
        runTest {
            val appleUser =
                FirebaseAuthUser(
                    uid = "apple-uid",
                    isAnonymous = false,
                    providerIds = setOf("apple.com"),
                )
            val gateway = FakeFirebaseAuthGateway(currentUser = null, appleUser = appleUser)
            val client = FirebaseAuthClient(gateway = gateway, coroutineScope = backgroundScope)

            val result =
                client.signInWithCredential(
                    NativeAuthCredential.Apple(idToken = "apple-id-token", rawNonce = "apple-raw-nonce"),
                )

            val session = assertIs<Outcome.Ok<AuthSession>>(result).value
            assertEquals("apple-uid", session.uid)
            assertEquals(false, session.isAnonymous)
            assertEquals(setOf(AuthProvider.APPLE), session.providers)
            assertEquals(AuthState.SignedIn(session), client.authState.value)
        }

    @Test
    fun cancelledGoogleSignInProducesAuthErrorCancelled() =
        runTest {
            val gateway =
                FakeFirebaseAuthGateway(
                    currentUser = null,
                    throwOnSignIn = FirebaseAuthGatewayException.Cancelled(RuntimeException("Dialog dismissed")),
                )
            val client = FirebaseAuthClient(gateway = gateway, coroutineScope = backgroundScope)

            val result =
                client.signInWithCredential(
                    NativeAuthCredential.Google(idToken = "id-token", accessToken = null),
                )

            val error = assertIs<Outcome.Err<AuthError>>(result).error
            assertEquals(AuthError.Cancelled, error)
        }

    @Test
    fun cancelledAppleSignInProducesAuthErrorCancelled() =
        runTest {
            val gateway =
                FakeFirebaseAuthGateway(
                    currentUser = null,
                    throwOnSignIn = FirebaseAuthGatewayException.Cancelled(RuntimeException("webContextCancelled")),
                )
            val client = FirebaseAuthClient(gateway = gateway, coroutineScope = backgroundScope)

            val result =
                client.signInWithCredential(
                    NativeAuthCredential.Apple(idToken = "id-token", rawNonce = "nonce"),
                )

            val error = assertIs<Outcome.Err<AuthError>>(result).error
            assertEquals(AuthError.Cancelled, error)
        }

    @Test
    fun networkFailureDuringSignInProducesAuthErrorNetworkUnavailable() =
        runTest {
            val gateway =
                FakeFirebaseAuthGateway(
                    currentUser = null,
                    throwOnSignIn = FirebaseAuthGatewayException.Network(RuntimeException("No connection")),
                )
            val client = FirebaseAuthClient(gateway = gateway, coroutineScope = backgroundScope)

            val result =
                client.signInWithCredential(
                    NativeAuthCredential.Google(idToken = "id-token", accessToken = null),
                )

            val error = assertIs<Outcome.Err<AuthError>>(result).error
            assertEquals(AuthError.NetworkUnavailable, error)
        }

    @Test
    fun linkCredentialPreservesUidAndAddsProvider() =
        runTest {
            val initialUser = anonymousUser("user-123")
            val linkedUser =
                FirebaseAuthUser(
                    uid = "user-123",
                    isAnonymous = false,
                    providerIds = setOf("google.com"),
                )
            val gateway = FakeFirebaseAuthGateway(currentUser = initialUser, linkedUser = linkedUser)
            val client = FirebaseAuthClient(gateway = gateway, coroutineScope = backgroundScope)

            val result =
                client.linkCredential(
                    NativeAuthCredential.Google(idToken = "google-id-token", accessToken = null),
                )

            val session = assertIs<Outcome.Ok<AuthSession>>(result).value
            assertEquals("user-123", session.uid)
            assertEquals(false, session.isAnonymous)
            assertEquals(setOf(AuthProvider.GOOGLE), session.providers)
            assertEquals(AuthState.SignedIn(session), client.authState.value)
        }

    @Test
    fun linkCredentialCollisionProducesCredentialAlreadyInUse() =
        runTest {
            val initialUser = anonymousUser("user-123")
            val gateway =
                FakeFirebaseAuthGateway(
                    currentUser = initialUser,
                    throwOnLink = FirebaseAuthGatewayException.UserCollision(RuntimeException("Credential collision")),
                )
            val client = FirebaseAuthClient(gateway = gateway, coroutineScope = backgroundScope)

            val result =
                client.linkCredential(
                    NativeAuthCredential.Google(idToken = "google-id-token", accessToken = null),
                )

            val error = assertIs<Outcome.Err<AuthError>>(result).error
            assertEquals(AuthError.CredentialAlreadyInUse, error)
            assertEquals(AuthState.SignedIn(initialUser.toSession()), client.authState.value)
        }

    @Test
    fun linkCredentialWithMismatchedUidAbortsWithUidWouldChange() =
        runTest {
            val initialUser = anonymousUser("user-123")
            val unexpectedUser =
                FirebaseAuthUser(
                    uid = "different-uid-456",
                    isAnonymous = false,
                    providerIds = setOf("google.com"),
                )
            val gateway = FakeFirebaseAuthGateway(currentUser = initialUser, linkedUser = unexpectedUser)
            val client = FirebaseAuthClient(gateway = gateway, coroutineScope = backgroundScope)

            val result =
                client.linkCredential(
                    NativeAuthCredential.Google(idToken = "google-id-token", accessToken = null),
                )

            val error = assertIs<Outcome.Err<AuthError>>(result).error
            assertEquals(AuthError.UidWouldChange, error)
        }

    @Test
    fun signInWithCredentialWhileAnonymousSessionActiveAbortsWithUidWouldChange() =
        runTest {
            val initialAnonymous = anonymousUser("user-123")
            val permanentUser =
                FirebaseAuthUser(
                    uid = "different-permanent-uid",
                    isAnonymous = false,
                    providerIds = setOf("google.com"),
                )
            val gateway = FakeFirebaseAuthGateway(currentUser = initialAnonymous, googleUser = permanentUser)
            val client = FirebaseAuthClient(gateway = gateway, coroutineScope = backgroundScope)

            val result =
                client.signInWithCredential(
                    NativeAuthCredential.Google(idToken = "google-id-token", accessToken = null),
                )

            val error = assertIs<Outcome.Err<AuthError>>(result).error
            assertEquals(AuthError.UidWouldChange, error)
            assertEquals(AuthState.SignedIn(initialAnonymous.toSession()), client.authState.value)
        }

    @Test
    fun signInWithCredentialWhenAnonymousAllowsUidChangeWhenExplicitlyRequested() =
        runTest {
            val initialAnonymous = anonymousUser("user-123")
            val permanentUser =
                FirebaseAuthUser(
                    uid = "different-permanent-uid",
                    isAnonymous = false,
                    providerIds = setOf("google.com"),
                )
            val gateway = FakeFirebaseAuthGateway(currentUser = initialAnonymous, googleUser = permanentUser)
            val client = FirebaseAuthClient(gateway = gateway, coroutineScope = backgroundScope)

            val result =
                client.signInWithCredential(
                    NativeAuthCredential.Google(idToken = "google-id-token", accessToken = null),
                    allowUidChange = true,
                )

            val session = assertIs<Outcome.Ok<AuthSession>>(result).value
            assertEquals("different-permanent-uid", session.uid)
            assertEquals(false, session.isAnonymous)
            assertEquals(AuthState.SignedIn(session), client.authState.value)
        }

    @Test
    fun reauthenticateWithCredentialSucceedsAndUpdatesSession() =
        runTest {
            val user =
                FirebaseAuthUser(
                    uid = "user-123",
                    isAnonymous = false,
                    providerIds = setOf("google.com"),
                )
            val gateway = FakeFirebaseAuthGateway(currentUser = user, reauthenticatedUser = user)
            val client = FirebaseAuthClient(gateway = gateway, coroutineScope = backgroundScope)

            val result =
                client.reauthenticate(
                    NativeAuthCredential.Google(idToken = "fresh-id-token", accessToken = null),
                )

            val session = assertIs<Outcome.Ok<AuthSession>>(result).value
            assertEquals("user-123", session.uid)
            assertEquals(AuthState.SignedIn(session), client.authState.value)
        }

    @Test
    fun reauthenticateForcesTokenRefreshAndReMintsToken() =
        runTest {
            val user =
                FirebaseAuthUser(
                    uid = "user-123",
                    isAnonymous = false,
                    providerIds = setOf("google.com"),
                )
            val gateway = FakeFirebaseAuthGateway(currentUser = user, reauthenticatedUser = user)
            val client = FirebaseAuthClient(gateway = gateway, coroutineScope = backgroundScope)

            val result =
                client.reauthenticate(
                    NativeAuthCredential.Google(idToken = "fresh-id-token", accessToken = null),
                )

            assertIs<Outcome.Ok<AuthSession>>(result)
            assertEquals(true, gateway.lastForceRefreshToken)
        }

    @Test
    fun signOutSignsOutFromGatewayAndPublishesSignedOutState() =
        runTest {
            val user = anonymousUser("active-user")
            val gateway = FakeFirebaseAuthGateway(currentUser = user)
            val client = FirebaseAuthClient(gateway = gateway, coroutineScope = backgroundScope)

            val result = client.signOut()

            assertIs<Outcome.Ok<Unit>>(result)
            assertTrue(gateway.signOutCalled)
            assertEquals(AuthState.SignedOut, client.authState.value)
        }

    @Test
    fun deleteAccountWithFreshTokenCallsServerOperationAndSucceeds() =
        runTest {
            val user = anonymousUser("to-delete-user")
            val issuedAt = Instant.fromEpochMilliseconds(1_000_000L)
            val nowInstant = issuedAt.plus(kotlin.time.Duration.parse("60s")) // 60s < 300s (FRESH_LOGIN_THRESHOLD_MS)
            val token = AuthToken("fresh-id-token", issuedAt, issuedAt.plus(kotlin.time.Duration.parse("1h")))

            val gateway = FakeFirebaseAuthGateway(currentUser = user, idTokenResult = token)
            val clock = AppClock { nowInstant }
            val client = FirebaseAuthClient(gateway = gateway, clock = clock, coroutineScope = backgroundScope)

            val result = client.deleteAccount()

            assertIs<Outcome.Ok<Unit>>(result)
            assertEquals("fresh-id-token", gateway.lastServerDeleteToken)
        }

    @Test
    fun deleteAccountWithStaleTokenProducesRequiresRecentLoginWithoutCallingServer() =
        runTest {
            val user = anonymousUser("stale-user")
            val issuedAt = Instant.fromEpochMilliseconds(1_000_000L)
            // Stale: elapsed is 301 seconds (> FRESH_LOGIN_THRESHOLD_MS = 300_000 ms)
            val nowInstant = Instant.fromEpochMilliseconds(1_000_000L + FRESH_LOGIN_THRESHOLD_MS + 1_000L)
            val token = AuthToken("stale-id-token", issuedAt, issuedAt.plus(kotlin.time.Duration.parse("1h")))

            val gateway = FakeFirebaseAuthGateway(currentUser = user, idTokenResult = token)
            val clock = AppClock { nowInstant }
            val client = FirebaseAuthClient(gateway = gateway, clock = clock, coroutineScope = backgroundScope)

            val result = client.deleteAccount()

            val error = assertIs<Outcome.Err<AuthError>>(result).error
            assertEquals(AuthError.RequiresRecentLogin, error)
            assertEquals(null, gateway.lastServerDeleteToken)
        }

    @Test
    fun deleteAccountMapsServerFailureToAccountDeletionRemoteFailed() =
        runTest {
            val user = anonymousUser("failing-delete-user")
            val issuedAt = Instant.fromEpochMilliseconds(1_000_000L)
            val token = AuthToken("token", issuedAt, issuedAt.plus(kotlin.time.Duration.parse("1h")))

            val gateway =
                FakeFirebaseAuthGateway(
                    currentUser = user,
                    idTokenResult = token,
                    throwOnDeleteServer =
                        FirebaseAuthGatewayException.AccountDeletionRemoteFailed(
                            RuntimeException("Server admin operation failed"),
                        ),
                )
            val clock = AppClock { issuedAt }
            val client = FirebaseAuthClient(gateway = gateway, clock = clock, coroutineScope = backgroundScope)

            val result = client.deleteAccount()

            val error = assertIs<Outcome.Err<AuthError>>(result).error
            assertEquals(AuthError.AccountDeletionRemoteFailed, error)
        }

    @Test
    fun deleteAccountMapsPermissionDeniedToPermissionDenied() =
        runTest {
            val user = anonymousUser("perm-denied-user")
            val issuedAt = Instant.fromEpochMilliseconds(1_000_000L)
            val token = AuthToken("token", issuedAt, issuedAt.plus(kotlin.time.Duration.parse("1h")))

            val gateway =
                FakeFirebaseAuthGateway(
                    currentUser = user,
                    idTokenResult = token,
                    throwOnDeleteServer =
                        FirebaseAuthGatewayException.PermissionDenied(
                            RuntimeException("Caller UID mismatch / IAM rejected"),
                        ),
                )
            val clock = AppClock { issuedAt }
            val client = FirebaseAuthClient(gateway = gateway, clock = clock, coroutineScope = backgroundScope)

            val result = client.deleteAccount()

            val error = assertIs<Outcome.Err<AuthError>>(result).error
            assertEquals(AuthError.PermissionDenied, error)
        }

    @Test
    fun deleteAccountMapsGetIdTokenFailureToGatewayError() =
        runTest {
            val user = anonymousUser("id-token-fail-user")
            val gateway =
                FakeFirebaseAuthGateway(
                    currentUser = user,
                    throwOnGetIdToken = FirebaseAuthGatewayException.Network(RuntimeException("Network down")),
                )
            val client =
                FirebaseAuthClient(
                    gateway = gateway,
                    clock = AppClock { Instant.fromEpochMilliseconds(1_000L) },
                    coroutineScope = backgroundScope,
                )

            val result = client.deleteAccount()

            val error = assertIs<Outcome.Err<AuthError>>(result).error
            assertEquals(AuthError.NetworkUnavailable, error)
        }

    @Test
    fun deleteAccountMapsNetworkFailureToNetworkUnavailable() =
        runTest {
            val user = anonymousUser("net-fail-user")
            val issuedAt = Instant.fromEpochMilliseconds(1_000_000L)
            val token = AuthToken("token", issuedAt, issuedAt.plus(kotlin.time.Duration.parse("1h")))

            val gateway =
                FakeFirebaseAuthGateway(
                    currentUser = user,
                    idTokenResult = token,
                    throwOnDeleteServer = FirebaseAuthGatewayException.Network(RuntimeException("Timeout")),
                )
            val clock = AppClock { issuedAt }
            val client = FirebaseAuthClient(gateway = gateway, clock = clock, coroutineScope = backgroundScope)

            val result = client.deleteAccount()

            val error = assertIs<Outcome.Err<AuthError>>(result).error
            assertEquals(AuthError.NetworkUnavailable, error)
        }

    @Test
    fun getIdTokenReturnsTokenWithIssuedAtAndExpiresAtTimestamps() =
        runTest {
            val issuedAt = Instant.fromEpochMilliseconds(1_700_000_000_000L)
            val expiresAt = Instant.fromEpochMilliseconds(1_700_003_600_000L)
            val token = AuthToken("valid-jwt-token", issuedAt, expiresAt)

            val gateway =
                FakeFirebaseAuthGateway(
                    currentUser = anonymousUser("uid"),
                    idTokenResult = token,
                )
            val client = FirebaseAuthClient(gateway = gateway, coroutineScope = backgroundScope)

            val result = client.getIdToken(forceRefresh = false)

            val tokenResult = assertIs<Outcome.Ok<AuthToken>>(result).value
            assertEquals("valid-jwt-token", tokenResult.value)
            assertEquals(issuedAt, tokenResult.issuedAt)
            assertEquals(expiresAt, tokenResult.expiresAt)
            assertEquals(false, gateway.lastForceRefreshToken)
        }

    @Test
    fun getIdTokenForwardsForceRefreshFlag() =
        runTest {
            val token =
                AuthToken(
                    "refreshed-token",
                    Instant.fromEpochMilliseconds(1_000),
                    Instant.fromEpochMilliseconds(3_600_000),
                )
            val gateway =
                FakeFirebaseAuthGateway(
                    currentUser = anonymousUser("uid"),
                    idTokenResult = token,
                )
            val client = FirebaseAuthClient(gateway = gateway, coroutineScope = backgroundScope)

            val result = client.getIdToken(forceRefresh = true)

            assertIs<Outcome.Ok<AuthToken>>(result)
            assertEquals(true, gateway.lastForceRefreshToken)
        }

    @Test
    fun deleteAccountDoesNotCallServerWhenTokenHasNoUsableIat() =
        runTest {
            val gateway =
                FakeFirebaseAuthGateway(
                    currentUser = FirebaseAuthUser(uid = "user-1", isAnonymous = false, providerIds = emptySet()),
                    tokenClaims = emptyMap(),
                )
            val client = FirebaseAuthClient(gateway = gateway, coroutineScope = backgroundScope)

            val outcome = client.deleteAccount()

            val error = assertIs<Outcome.Err<AuthError>>(outcome).error
            assertEquals(AuthError.ProviderUnavailable, error)
            assertEquals(null, gateway.lastServerDeleteToken)
        }

    @Test
    fun deleteAccountPropagatesPermissionDeniedFromDeletionInvoker() =
        runTest {
            val issuedAt = Instant.fromEpochMilliseconds(1_000_000L)
            val token = AuthToken("token", issuedAt, issuedAt + 1.hours)
            val gateway =
                FakeFirebaseAuthGateway(
                    currentUser = FirebaseAuthUser(uid = "user-1", isAnonymous = false, providerIds = emptySet()),
                    idTokenResult = token,
                    throwOnDeleteServer =
                        FirebaseAuthGatewayException.PermissionDenied(
                            IllegalStateException("Caller UID mismatch"),
                        ),
                )
            val client =
                FirebaseAuthClient(
                    gateway = gateway,
                    clock = AppClock { issuedAt },
                    coroutineScope = backgroundScope,
                )

            val outcome = client.deleteAccount()

            val error = assertIs<Outcome.Err<AuthError>>(outcome).error
            assertEquals(AuthError.PermissionDenied, error)
        }

    @Test
    fun closeCancelsAuthStateObservation() =
        runTest {
            val gateway = FakeFirebaseAuthGateway(currentUser = null, autoEmitAuthState = false)
            val client = FirebaseAuthClient(gateway = gateway, coroutineScope = backgroundScope)
            assertEquals(AuthState.Unknown, client.authState.value)

            val user1 = FirebaseAuthUser(uid = "uid-1", isAnonymous = true, providerIds = emptySet())
            gateway.emitAuthState(user1)
            runCurrent()
            assertEquals(AuthState.SignedIn(user1.toSession()), client.authState.value)

            client.close()

            val user2 = FirebaseAuthUser(uid = "uid-2", isAnonymous = false, providerIds = setOf("google.com"))
            gateway.emitAuthState(user2)
            runCurrent()
            assertEquals(AuthState.SignedIn(user1.toSession()), client.authState.value)
        }

    @Test
    fun parseCreationTimeParsesAndroidMilliseconds() {
        val androidMillis = 1_700_000_000_000.0
        val parsed = parseCreationTime(androidMillis)
        assertEquals(Instant.fromEpochMilliseconds(1_700_000_000_000L), parsed)
    }

    @Test
    fun parseCreationTimeParsesAppleReferenceDateSeconds() {
        val appleZero = 0.1
        val parsed = parseCreationTime(appleZero)
        assertEquals(Instant.fromEpochMilliseconds(978_307_200_100L), parsed)
    }

    @Test
    fun parseClaimTimestampParsesSecondsAndMilliseconds() {
        val seconds = parseClaimTimestamp(1_700_000_000L)
        assertEquals(Instant.fromEpochMilliseconds(1_700_000_000_000L), seconds)

        val millis = parseClaimTimestamp(1_700_000_000_000L)
        assertEquals(Instant.fromEpochMilliseconds(1_700_000_000_000L), millis)

        val strSeconds = parseClaimTimestamp("1700000000")
        assertEquals(Instant.fromEpochMilliseconds(1_700_000_000_000L), strSeconds)
    }
}

private class FakeFirebaseAuthGateway(
    override var currentUser: FirebaseAuthUser? = null,
    private val signedInUser: FirebaseAuthUser? = currentUser,
    var googleUser: FirebaseAuthUser? = null,
    var appleUser: FirebaseAuthUser? = null,
    var linkedUser: FirebaseAuthUser? = null,
    var reauthenticatedUser: FirebaseAuthUser? = null,
    var idTokenResult: AuthToken =
        AuthToken(
            "fake-token",
            Instant.fromEpochMilliseconds(1_000),
            Instant.fromEpochMilliseconds(3_600_000),
        ),
    var throwOnSignIn: FirebaseAuthGatewayException? = null,
    var throwOnLink: FirebaseAuthGatewayException? = null,
    var throwOnReauth: FirebaseAuthGatewayException? = null,
    var throwOnGetIdToken: FirebaseAuthGatewayException? = null,
    var throwOnDeleteServer: FirebaseAuthGatewayException? = null,
    var tokenClaims: Map<String, Any>? = null,
    autoEmitAuthState: Boolean = true,
) : FirebaseAuthGateway {
    private val _authStateChanged = kotlinx.coroutines.flow.MutableSharedFlow<FirebaseAuthUser?>(replay = 1)
    override val authStateChanged: kotlinx.coroutines.flow.Flow<FirebaseAuthUser?> = _authStateChanged

    init {
        if (autoEmitAuthState) {
            _authStateChanged.tryEmit(currentUser)
        }
    }

    fun emitAuthState(user: FirebaseAuthUser?) {
        currentUser = user
        _authStateChanged.tryEmit(user)
    }

    var signOutCalled: Boolean = false
    var lastServerDeleteToken: String? = null
    var lastForceRefreshToken: Boolean? = null

    override suspend fun signInAnonymously(): FirebaseAuthUser {
        throwOnSignIn?.let { throw it }
        return checkNotNull(signedInUser).also {
            currentUser = it
            _authStateChanged.tryEmit(it)
        }
    }

    override suspend fun signInWithGoogle(
        idToken: String,
        accessToken: String?,
    ): FirebaseAuthUser {
        throwOnSignIn?.let { throw it }
        return checkNotNull(googleUser).also {
            currentUser = it
            _authStateChanged.tryEmit(it)
        }
    }

    override suspend fun signInWithApple(
        idToken: String,
        rawNonce: String,
    ): FirebaseAuthUser {
        throwOnSignIn?.let { throw it }
        return checkNotNull(appleUser).also {
            currentUser = it
            _authStateChanged.tryEmit(it)
        }
    }

    override suspend fun linkGoogle(
        idToken: String,
        accessToken: String?,
    ): FirebaseAuthUser {
        throwOnLink?.let { throw it }
        return checkNotNull(linkedUser).also {
            currentUser = it
            _authStateChanged.tryEmit(it)
        }
    }

    override suspend fun linkApple(
        idToken: String,
        rawNonce: String,
    ): FirebaseAuthUser {
        throwOnLink?.let { throw it }
        return checkNotNull(linkedUser).also {
            currentUser = it
            _authStateChanged.tryEmit(it)
        }
    }

    override suspend fun reauthenticateWithGoogle(
        idToken: String,
        accessToken: String?,
    ): FirebaseAuthUser {
        throwOnReauth?.let { throw it }
        return checkNotNull(reauthenticatedUser).also {
            currentUser = it
            _authStateChanged.tryEmit(it)
        }
    }

    override suspend fun reauthenticateWithApple(
        idToken: String,
        rawNonce: String,
    ): FirebaseAuthUser {
        throwOnReauth?.let { throw it }
        return checkNotNull(reauthenticatedUser).also {
            currentUser = it
            _authStateChanged.tryEmit(it)
        }
    }

    override suspend fun signOut() {
        signOutCalled = true
        currentUser = null
        _authStateChanged.tryEmit(null)
    }

    override suspend fun getIdToken(forceRefresh: Boolean): AuthToken {
        throwOnGetIdToken?.let { throw it }
        tokenClaims?.let { claims ->
            val (issuedAt, expiresAt) = parseTokenTimestamps(claims)
            return AuthToken(value = "token-from-claims", issuedAt = issuedAt, expiresAt = expiresAt)
        }
        lastForceRefreshToken = forceRefresh
        return idTokenResult
    }

    override suspend fun executeServerAccountDeletion(idToken: String) {
        throwOnDeleteServer?.let { throw it }
        lastServerDeleteToken = idToken
    }
}

private fun anonymousUser(
    uid: String,
    createdAt: Instant? = null,
): FirebaseAuthUser =
    FirebaseAuthUser(
        uid = uid,
        isAnonymous = true,
        providerIds = emptySet(),
        createdAt = createdAt,
    )
