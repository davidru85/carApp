package com.ruizurraca.carapp.core.auth

import com.ruizurraca.carapp.core.common.AuthError
import com.ruizurraca.carapp.core.common.AuthProvider
import com.ruizurraca.carapp.core.common.Outcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.time.Instant

class AuthContractsTest {
    @Test
    fun authSessionRetainsItsIdentityAndProviders() {
        val session =
            AuthSession(
                uid = "owner-1",
                isAnonymous = false,
                providers = setOf(AuthProvider.GOOGLE, AuthProvider.APPLE),
            )

        assertEquals("owner-1", session.uid)
        assertEquals(false, session.isAnonymous)
        assertEquals(setOf(AuthProvider.GOOGLE, AuthProvider.APPLE), session.providers)
    }

    @Test
    fun unknownAuthStateIsDistinctFromSignedOut() {
        assertNotEquals<AuthState>(AuthState.Unknown, AuthState.SignedOut)
    }

    @Test
    fun signedInAuthStateRetainsItsSession() {
        val session = anonymousSession("owner-1")

        assertEquals(session, AuthState.SignedIn(session).session)
    }

    @Test
    fun googleCredentialRetainsItsNativeTokens() {
        val credential = NativeAuthCredential.Google(idToken = "id-token", accessToken = "access-token")

        assertEquals("id-token", credential.idToken)
        assertEquals("access-token", credential.accessToken)
    }

    @Test
    fun appleCredentialRetainsItsNativeTokens() {
        val credential = NativeAuthCredential.Apple(idToken = "id-token", rawNonce = "raw-nonce")

        assertEquals("id-token", credential.idToken)
        assertEquals("raw-nonce", credential.rawNonce)
    }

    @Test
    fun authTokenRetainsItsValueAndValidityWindow() {
        val issuedAt = Instant.fromEpochMilliseconds(1_000)
        val expiresAt = Instant.fromEpochMilliseconds(2_000)
        val token = AuthToken(value = "token", issuedAt = issuedAt, expiresAt = expiresAt)

        assertEquals("token", token.value)
        assertEquals(issuedAt, token.issuedAt)
        assertEquals(expiresAt, token.expiresAt)
    }

    /**
     * Pins the [AuthClient] and [TokenProvider] method signatures at compile time per
     * `docs/CONTRACTS.md §11.1` and `§20.8`.
     *
     * The force of this test is compile-time signature conformance, ensuring method return types
     * and parameters conform to the contract specifications. The runtime assertions inspect the
     * concrete [AuthError] leaf rather than relying on erased generic type checks.
     */
    @Test
    fun authClientAndTokenProviderMatchContractSignaturesAtCompileTime() =
        runTest {
            val client = ContractAuthClient()
            val credential = NativeAuthCredential.Google(idToken = "id-token", accessToken = null)

            assertEquals(
                AuthError.ProviderUnavailable,
                (client.signInAnonymously() as Outcome.Err).error,
            )
            assertEquals(
                AuthError.ProviderUnavailable,
                (client.signInWithCredential(credential) as Outcome.Err).error,
            )
            assertEquals(
                AuthError.ProviderUnavailable,
                (client.linkCredential(credential) as Outcome.Err).error,
            )
            assertEquals(
                AuthError.ProviderUnavailable,
                (client.reauthenticate(credential) as Outcome.Err).error,
            )
            assertEquals(
                AuthError.ProviderUnavailable,
                (client.signOut() as Outcome.Err).error,
            )
            assertEquals(
                AuthError.ProviderUnavailable,
                (client.deleteAccount() as Outcome.Err).error,
            )
            assertEquals(
                AuthError.ProviderUnavailable,
                (client.getIdToken(forceRefresh = true) as Outcome.Err).error,
            )
        }
}

private class ContractAuthClient :
    AuthClient,
    TokenProvider {
    override val authState: StateFlow<AuthState> = MutableStateFlow(AuthState.Unknown)

    override suspend fun signInAnonymously(): Outcome<AuthSession, AuthError> = unavailable()

    override suspend fun signInWithCredential(credential: NativeAuthCredential): Outcome<AuthSession, AuthError> =
        unavailable()

    override suspend fun linkCredential(credential: NativeAuthCredential): Outcome<AuthSession, AuthError> =
        unavailable()

    override suspend fun reauthenticate(credential: NativeAuthCredential): Outcome<AuthSession, AuthError> =
        unavailable()

    override suspend fun signOut(): Outcome<Unit, AuthError> = unavailable()

    override suspend fun deleteAccount(): Outcome<Unit, AuthError> = unavailable()

    override suspend fun getIdToken(forceRefresh: Boolean): Outcome<AuthToken, AuthError> = unavailable()
}

private fun <T> unavailable(): Outcome<T, AuthError> = Outcome.Err(AuthError.ProviderUnavailable)

private fun anonymousSession(uid: String): AuthSession =
    AuthSession(
        uid = uid,
        isAnonymous = true,
        providers = setOf(AuthProvider.ANONYMOUS),
    )
