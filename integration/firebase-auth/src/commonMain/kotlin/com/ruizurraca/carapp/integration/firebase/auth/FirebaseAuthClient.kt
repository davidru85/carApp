package com.ruizurraca.carapp.integration.firebase.auth

import com.ruizurraca.carapp.core.auth.AuthClient
import com.ruizurraca.carapp.core.auth.AuthSession
import com.ruizurraca.carapp.core.auth.AuthState
import com.ruizurraca.carapp.core.auth.NativeAuthCredential
import com.ruizurraca.carapp.core.common.AuthError
import com.ruizurraca.carapp.core.common.AuthProvider
import com.ruizurraca.carapp.core.common.Outcome
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.FirebaseUser
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Firebase-backed authentication boundary. Later auth stories complete the non-anonymous flows. */
class FirebaseAuthClient internal constructor(
    gateway: FirebaseAuthGateway,
) : AuthClient {
    constructor() : this(GitLiveFirebaseAuthGateway())

    private val mutableAuthState = MutableStateFlow(gateway.currentUser.toAuthState())
    override val authState: StateFlow<AuthState> = mutableAuthState

    override suspend fun signInAnonymously(): Outcome<AuthSession, AuthError> = providerUnavailable()

    override suspend fun signInWithCredential(credential: NativeAuthCredential): Outcome<AuthSession, AuthError> =
        providerUnavailable()

    override suspend fun linkCredential(credential: NativeAuthCredential): Outcome<AuthSession, AuthError> =
        providerUnavailable()

    override suspend fun reauthenticate(credential: NativeAuthCredential): Outcome<AuthSession, AuthError> =
        providerUnavailable()

    override suspend fun signOut(): Outcome<Unit, AuthError> = providerUnavailable()

    override suspend fun deleteAccount(): Outcome<Unit, AuthError> = providerUnavailable()
}

internal interface FirebaseAuthGateway {
    val currentUser: FirebaseAuthUser?
}

internal data class FirebaseAuthUser(
    val uid: String,
    val isAnonymous: Boolean,
    val providerIds: Set<String>,
)

private class GitLiveFirebaseAuthGateway : FirebaseAuthGateway {
    override val currentUser: FirebaseAuthUser?
        get() = Firebase.auth.currentUser?.toIntegrationUser()
}

private fun FirebaseUser.toIntegrationUser(): FirebaseAuthUser =
    FirebaseAuthUser(
        uid = uid,
        isAnonymous = isAnonymous,
        providerIds = providerData.mapTo(mutableSetOf()) { userInfo -> userInfo.providerId },
    )

private fun FirebaseAuthUser?.toAuthState(): AuthState =
    this?.let { user -> AuthState.SignedIn(user.toSession()) } ?: AuthState.SignedOut

private fun FirebaseAuthUser.toSession(): AuthSession =
    AuthSession(
        uid = uid,
        isAnonymous = isAnonymous,
        providers =
            buildSet {
                if (isAnonymous) add(AuthProvider.ANONYMOUS)
                if (GOOGLE_PROVIDER_ID in providerIds) add(AuthProvider.GOOGLE)
                if (APPLE_PROVIDER_ID in providerIds) add(AuthProvider.APPLE)
            },
    )

private fun <T> providerUnavailable(): Outcome<T, AuthError> = Outcome.Err(AuthError.ProviderUnavailable)

private const val GOOGLE_PROVIDER_ID = "google.com"
private const val APPLE_PROVIDER_ID = "apple.com"
