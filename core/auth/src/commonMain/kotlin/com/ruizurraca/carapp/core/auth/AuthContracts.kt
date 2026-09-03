package com.ruizurraca.carapp.core.auth

import com.ruizurraca.carapp.core.common.AuthError
import com.ruizurraca.carapp.core.common.AuthProvider
import com.ruizurraca.carapp.core.common.Outcome
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Instant

data class AuthSession(
    val uid: String,
    val isAnonymous: Boolean,
    val providers: Set<AuthProvider>,
    val createdAt: Instant? = null,
)

sealed interface AuthState {
    data object Unknown : AuthState

    data object SignedOut : AuthState

    data class SignedIn(
        val session: AuthSession,
    ) : AuthState
}

sealed interface NativeAuthCredential {
    data class Google(
        val idToken: String,
        val accessToken: String?,
    ) : NativeAuthCredential

    data class Apple(
        val idToken: String,
        val rawNonce: String,
    ) : NativeAuthCredential
}

data class AuthToken(
    val value: String,
    val issuedAt: Instant,
    val expiresAt: Instant,
)

interface TokenProvider {
    suspend fun getIdToken(forceRefresh: Boolean): Outcome<AuthToken, AuthError>
}

interface AuthClient {
    val authState: StateFlow<AuthState>

    suspend fun signInAnonymously(): Outcome<AuthSession, AuthError>

    suspend fun signInWithCredential(
        credential: NativeAuthCredential,
        allowUidChange: Boolean = false,
    ): Outcome<AuthSession, AuthError>

    suspend fun linkCredential(credential: NativeAuthCredential): Outcome<AuthSession, AuthError>

    suspend fun reauthenticate(credential: NativeAuthCredential): Outcome<AuthSession, AuthError>

    suspend fun signOut(): Outcome<Unit, AuthError>

    suspend fun deleteAccount(): Outcome<Unit, AuthError>
}
