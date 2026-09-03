package com.ruizurraca.carapp.integration.firebase.auth

import com.ruizurraca.carapp.core.auth.AuthClient
import com.ruizurraca.carapp.core.auth.AuthSession
import com.ruizurraca.carapp.core.auth.AuthState
import com.ruizurraca.carapp.core.auth.AuthToken
import com.ruizurraca.carapp.core.auth.NativeAuthCredential
import com.ruizurraca.carapp.core.auth.TokenProvider
import com.ruizurraca.carapp.core.common.AppClock
import com.ruizurraca.carapp.core.common.AuthError
import com.ruizurraca.carapp.core.common.AuthProvider
import com.ruizurraca.carapp.core.common.FRESH_LOGIN_THRESHOLD_MS
import com.ruizurraca.carapp.core.common.Outcome
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseApiNotAvailableException
import dev.gitlive.firebase.FirebaseException
import dev.gitlive.firebase.FirebaseNetworkException
import dev.gitlive.firebase.auth.FirebaseAuthException
import dev.gitlive.firebase.auth.FirebaseAuthInvalidCredentialsException
import dev.gitlive.firebase.auth.FirebaseAuthRecentLoginRequiredException
import dev.gitlive.firebase.auth.FirebaseAuthUserCollisionException
import dev.gitlive.firebase.auth.FirebaseAuthWebException
import dev.gitlive.firebase.auth.FirebaseUser
import dev.gitlive.firebase.auth.GoogleAuthProvider
import dev.gitlive.firebase.auth.OAuthProvider
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.auth.code
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** Firebase-backed authentication boundary. Later auth stories complete the non-anonymous flows. */
class FirebaseAuthClient internal constructor(
    private val gateway: FirebaseAuthGateway,
    private val clock: AppClock = AppClock { Clock.System.now() },
    coroutineScope: kotlinx.coroutines.CoroutineScope =
        kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default,
        ),
) : AuthClient,
    TokenProvider {
    constructor() : this(GitLiveFirebaseAuthGateway())

    private val mutableAuthState = MutableStateFlow<AuthState>(AuthState.Unknown)
    override val authState: StateFlow<AuthState> = mutableAuthState

    init {
        coroutineScope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            gateway.authStateChanged.collect { firebaseUser ->
                mutableAuthState.value = firebaseUser.toAuthState()
            }
        }
    }

    override suspend fun signInAnonymously(): Outcome<AuthSession, AuthError> =
        try {
            val session = gateway.signInAnonymously().toSession()
            mutableAuthState.value = AuthState.SignedIn(session)
            Outcome.Ok(session)
        } catch (failure: FirebaseAuthGatewayException) {
            Outcome.Err(failure.toAuthError())
        }

    override suspend fun signInWithCredential(
        credential: NativeAuthCredential,
        allowUidChange: Boolean,
    ): Outcome<AuthSession, AuthError> =
        try {
            val current = gateway.currentUser
            if (current != null && current.isAnonymous && !allowUidChange) {
                Outcome.Err(AuthError.UidWouldChange)
            } else {
                val user =
                    when (credential) {
                        is NativeAuthCredential.Google -> {
                            gateway.signInWithGoogle(credential.idToken, credential.accessToken)
                        }

                        is NativeAuthCredential.Apple -> {
                            gateway.signInWithApple(credential.idToken, credential.rawNonce)
                        }
                    }
                val session = user.toSession()
                mutableAuthState.value = AuthState.SignedIn(session)
                Outcome.Ok(session)
            }
        } catch (failure: FirebaseAuthGatewayException) {
            Outcome.Err(failure.toAuthError())
        }

    override suspend fun linkCredential(credential: NativeAuthCredential): Outcome<AuthSession, AuthError> =
        try {
            val current = gateway.currentUser ?: return Outcome.Err(AuthError.ProviderUnavailable)
            val user =
                when (credential) {
                    is NativeAuthCredential.Google -> {
                        gateway.linkGoogle(credential.idToken, credential.accessToken)
                    }

                    is NativeAuthCredential.Apple -> {
                        gateway.linkApple(credential.idToken, credential.rawNonce)
                    }
                }
            if (user.uid != current.uid) {
                Outcome.Err(AuthError.UidWouldChange)
            } else {
                val session = user.toSession()
                mutableAuthState.value = AuthState.SignedIn(session)
                Outcome.Ok(session)
            }
        } catch (failure: FirebaseAuthGatewayException) {
            Outcome.Err(failure.toAuthError())
        }

    override suspend fun reauthenticate(credential: NativeAuthCredential): Outcome<AuthSession, AuthError> =
        try {
            if (gateway.currentUser == null) return Outcome.Err(AuthError.ProviderUnavailable)
            val user =
                when (credential) {
                    is NativeAuthCredential.Google -> {
                        gateway.reauthenticateWithGoogle(credential.idToken, credential.accessToken)
                    }

                    is NativeAuthCredential.Apple -> {
                        gateway.reauthenticateWithApple(credential.idToken, credential.rawNonce)
                    }
                }
            gateway.getIdToken(forceRefresh = true)
            val session = user.toSession()
            mutableAuthState.value = AuthState.SignedIn(session)
            Outcome.Ok(session)
        } catch (failure: FirebaseAuthGatewayException) {
            Outcome.Err(failure.toAuthError())
        }

    override suspend fun signOut(): Outcome<Unit, AuthError> =
        try {
            gateway.signOut()
            mutableAuthState.value = AuthState.SignedOut
            Outcome.Ok(Unit)
        } catch (failure: FirebaseAuthGatewayException) {
            Outcome.Err(failure.toAuthError())
        }

    override suspend fun deleteAccount(): Outcome<Unit, AuthError> =
        try {
            if (gateway.currentUser == null) return Outcome.Err(AuthError.ProviderUnavailable)
            val token =
                try {
                    gateway.getIdToken(forceRefresh = false)
                } catch (failure: FirebaseAuthGatewayException) {
                    return Outcome.Err(failure.toAuthError())
                }
            val ageMillis = (clock.now() - token.issuedAt).inWholeMilliseconds
            if (ageMillis > FRESH_LOGIN_THRESHOLD_MS) {
                return Outcome.Err(AuthError.RequiresRecentLogin)
            }
            gateway.executeServerAccountDeletion(token.value)
            Outcome.Ok(Unit)
        } catch (failure: FirebaseAuthGatewayException) {
            Outcome.Err(failure.toAuthError())
        }

    override suspend fun getIdToken(forceRefresh: Boolean): Outcome<AuthToken, AuthError> =
        try {
            if (gateway.currentUser == null) return Outcome.Err(AuthError.ProviderUnavailable)
            val token = gateway.getIdToken(forceRefresh)
            Outcome.Ok(token)
        } catch (failure: FirebaseAuthGatewayException) {
            Outcome.Err(failure.toAuthError())
        }
}

internal interface FirebaseAuthGateway {
    val currentUser: FirebaseAuthUser?

    val authStateChanged: kotlinx.coroutines.flow.Flow<FirebaseAuthUser?> get() = kotlinx.coroutines.flow.emptyFlow()

    suspend fun signInAnonymously(): FirebaseAuthUser

    suspend fun signInWithGoogle(
        idToken: String,
        accessToken: String?,
    ): FirebaseAuthUser = throw UnsupportedOperationException()

    suspend fun signInWithApple(
        idToken: String,
        rawNonce: String,
    ): FirebaseAuthUser = throw UnsupportedOperationException()

    suspend fun linkGoogle(
        idToken: String,
        accessToken: String?,
    ): FirebaseAuthUser = throw UnsupportedOperationException()

    suspend fun linkApple(
        idToken: String,
        rawNonce: String,
    ): FirebaseAuthUser = throw UnsupportedOperationException()

    suspend fun reauthenticateWithGoogle(
        idToken: String,
        accessToken: String?,
    ): FirebaseAuthUser = throw UnsupportedOperationException()

    suspend fun reauthenticateWithApple(
        idToken: String,
        rawNonce: String,
    ): FirebaseAuthUser = throw UnsupportedOperationException()

    suspend fun signOut(): Unit = throw UnsupportedOperationException()

    suspend fun getIdToken(forceRefresh: Boolean): AuthToken = throw UnsupportedOperationException()

    suspend fun executeServerAccountDeletion(idToken: String): Unit = throw UnsupportedOperationException()
}

internal data class FirebaseAuthUser(
    val uid: String,
    val isAnonymous: Boolean,
    val providerIds: Set<String>,
    val createdAt: Instant? = null,
)

internal fun interface AccountDeletionInvoker {
    suspend fun invokeDeletion(idToken: String)
}

internal class GitLiveFirebaseAuthGateway(
    private val deletionInvoker: AccountDeletionInvoker =
        AccountDeletionInvoker {
            throw FirebaseAuthGatewayException.AccountDeletionRemoteFailed(
                IllegalStateException("D-23 server account deletion operation is not yet configured on this client"),
            )
        },
) : FirebaseAuthGateway {
    override val authStateChanged: Flow<FirebaseAuthUser?> =
        Firebase.auth.authStateChanged.map { it?.toIntegrationUser() }

    override val currentUser: FirebaseAuthUser?
        get() =
            try {
                Firebase.auth.currentUser?.toIntegrationUser()
            } catch (_: FirebaseException) {
                null
            } catch (_: IllegalStateException) {
                null
            }

    override suspend fun signInAnonymously(): FirebaseAuthUser =
        safeAuthCall {
            checkNotNull(Firebase.auth.signInAnonymously().user).toIntegrationUser()
        }

    override suspend fun signInWithGoogle(
        idToken: String,
        accessToken: String?,
    ): FirebaseAuthUser =
        safeAuthCall {
            val credential = GoogleAuthProvider.credential(idToken = idToken, accessToken = accessToken)
            checkNotNull(Firebase.auth.signInWithCredential(credential).user).toIntegrationUser()
        }

    override suspend fun signInWithApple(
        idToken: String,
        rawNonce: String,
    ): FirebaseAuthUser =
        safeAuthCall {
            val credential =
                OAuthProvider.credential(
                    providerId = APPLE_PROVIDER_ID,
                    idToken = idToken,
                    rawNonce = rawNonce,
                )
            checkNotNull(Firebase.auth.signInWithCredential(credential).user).toIntegrationUser()
        }

    override suspend fun linkGoogle(
        idToken: String,
        accessToken: String?,
    ): FirebaseAuthUser =
        safeAuthCall {
            val user = checkNotNull(Firebase.auth.currentUser)
            val credential = GoogleAuthProvider.credential(idToken = idToken, accessToken = accessToken)
            checkNotNull(user.linkWithCredential(credential).user).toIntegrationUser()
        }

    override suspend fun linkApple(
        idToken: String,
        rawNonce: String,
    ): FirebaseAuthUser =
        safeAuthCall {
            val user = checkNotNull(Firebase.auth.currentUser)
            val credential =
                OAuthProvider.credential(
                    providerId = APPLE_PROVIDER_ID,
                    idToken = idToken,
                    rawNonce = rawNonce,
                )
            checkNotNull(user.linkWithCredential(credential).user).toIntegrationUser()
        }

    override suspend fun reauthenticateWithGoogle(
        idToken: String,
        accessToken: String?,
    ): FirebaseAuthUser =
        safeAuthCall {
            val user = checkNotNull(Firebase.auth.currentUser)
            val credential = GoogleAuthProvider.credential(idToken = idToken, accessToken = accessToken)
            user.reauthenticate(credential)
            checkNotNull(Firebase.auth.currentUser).toIntegrationUser()
        }

    override suspend fun reauthenticateWithApple(
        idToken: String,
        rawNonce: String,
    ): FirebaseAuthUser =
        safeAuthCall {
            val user = checkNotNull(Firebase.auth.currentUser)
            val credential =
                OAuthProvider.credential(
                    providerId = APPLE_PROVIDER_ID,
                    idToken = idToken,
                    rawNonce = rawNonce,
                )
            user.reauthenticate(credential)
            checkNotNull(Firebase.auth.currentUser).toIntegrationUser()
        }

    override suspend fun signOut() =
        safeAuthCall {
            Firebase.auth.signOut()
        }

    override suspend fun getIdToken(forceRefresh: Boolean): AuthToken =
        safeAuthCall {
            val user = checkNotNull(Firebase.auth.currentUser)
            val tokenResult = user.getIdTokenResult(forceRefresh)
            val tokenString = checkNotNull(tokenResult.token)
            val (issuedAt, expiresAt) = parseTokenTimestamps(tokenResult.claims, Clock.System.now())
            AuthToken(value = tokenString, issuedAt = issuedAt, expiresAt = expiresAt)
        }

    override suspend fun executeServerAccountDeletion(idToken: String) =
        safeAuthCall {
            deletionInvoker.invokeDeletion(idToken)
        }
}

private suspend inline fun <T> safeAuthCall(block: () -> T): T =
    try {
        block()
    } catch (failure: FirebaseAuthGatewayException) {
        throw failure
    } catch (failure: CancellationException) {
        throw failure
    } catch (failure: FirebaseException) {
        throw mapAuthException(failure)
    } catch (failure: IllegalStateException) {
        throw FirebaseAuthGatewayException.Unknown(failure)
    }

internal fun mapAuthException(failure: FirebaseException): FirebaseAuthGatewayException =
    when (failure) {
        is FirebaseAuthUserCollisionException -> FirebaseAuthGatewayException.UserCollision(failure)
        is FirebaseAuthRecentLoginRequiredException -> FirebaseAuthGatewayException.RequiresRecentLogin(failure)
        is FirebaseAuthWebException -> mapWebException(failure)
        is FirebaseAuthInvalidCredentialsException -> mapInvalidCredentialsException(failure)
        is FirebaseNetworkException -> FirebaseAuthGatewayException.Network(failure)
        is FirebaseApiNotAvailableException -> FirebaseAuthGatewayException.Provider(failure)
        is FirebaseAuthException -> mapFirebaseAuthException(failure)
        else -> mapGenericFirebaseException(failure)
    }

internal fun mapWebException(failure: FirebaseAuthWebException): FirebaseAuthGatewayException =
    if (isCancellation(failure.code, failure.message)) {
        FirebaseAuthGatewayException.Cancelled(failure)
    } else {
        FirebaseAuthGatewayException.Provider(failure)
    }

internal fun mapInvalidCredentialsException(
    failure: FirebaseAuthInvalidCredentialsException,
): FirebaseAuthGatewayException =
    if (isCancellation(failure.code, failure.message)) {
        FirebaseAuthGatewayException.Cancelled(failure)
    } else {
        FirebaseAuthGatewayException.Provider(failure)
    }

internal fun mapFirebaseAuthException(failure: FirebaseAuthException): FirebaseAuthGatewayException {
    if (isCancellation(failure.code, failure.message)) {
        return FirebaseAuthGatewayException.Cancelled(failure)
    }
    return when (failure.code) {
        FIREBASE_COLLISION_CODE, ERROR_CREDENTIAL_ALREADY_IN_USE -> {
            FirebaseAuthGatewayException.UserCollision(failure)
        }

        FIREBASE_RECENT_LOGIN_CODE, ERROR_REQUIRES_RECENT_LOGIN -> {
            FirebaseAuthGatewayException.RequiresRecentLogin(failure)
        }

        FIREBASE_NETWORK_CODE, ERROR_NETWORK_REQUEST_FAILED -> {
            FirebaseAuthGatewayException.Network(failure)
        }

        FIREBASE_PERMISSION_DENIED_CODE, ERROR_PERMISSION_DENIED -> {
            FirebaseAuthGatewayException.PermissionDenied(failure)
        }

        else -> {
            FirebaseAuthGatewayException.Provider(failure)
        }
    }
}

internal fun mapGenericFirebaseException(failure: FirebaseException): FirebaseAuthGatewayException =
    if (isCancellation(null, failure.message)) {
        FirebaseAuthGatewayException.Cancelled(failure)
    } else {
        FirebaseAuthGatewayException.Unknown(failure)
    }

internal fun isCancellation(
    code: String?,
    message: String?,
): Boolean {
    if (code == FIREBASE_WEB_CANCEL_CODE || code == ERROR_WEB_CONTEXT_CANCELLED) return true
    val msg = message?.lowercase() ?: return false
    return "cancel" in msg || "dismiss" in msg || FIREBASE_WEB_CANCEL_CODE in msg
}

/**
 * Normalizes Firebase user metadata creation time across platforms.
 *
 * In GitLive 2.6.0, `FirebaseUserMetadata.creationTime` returns a Double representing epoch milliseconds
 * on Android, but seconds since Apple reference date (2001-01-01 00:00:00 UTC) on Apple targets (iOS).
 * This function detects the platform representation by range check and converts it to a standard UTC [Instant].
 */
internal fun parseCreationTime(creationTime: Double?): Instant? {
    if (creationTime == null || creationTime <= 0.0) return null
    val epochMillis =
        if (creationTime > EPOCH_SECONDS_CUTOFF_DOUBLE) {
            creationTime.toLong()
        } else {
            ((creationTime + APPLE_REFERENCE_DATE_OFFSET_SECONDS) * MILLIS_PER_SECOND_DOUBLE).toLong()
        }
    return Instant.fromEpochMilliseconds(epochMillis)
}

internal fun parseClaimTimestamp(value: Any?): Instant? =
    when (value) {
        is Number -> {
            val num = value.toLong()
            val epochMillis = if (num < EPOCH_SECONDS_CUTOFF) num * MILLIS_PER_SECOND else num
            Instant.fromEpochMilliseconds(epochMillis)
        }

        is String -> {
            value.toLongOrNull()?.let { num ->
                val epochMillis = if (num < EPOCH_SECONDS_CUTOFF) num * MILLIS_PER_SECOND else num
                Instant.fromEpochMilliseconds(epochMillis)
            }
        }

        else -> {
            null
        }
    }

internal fun parseTokenTimestamps(
    claims: Map<String, Any>,
    now: Instant,
): Pair<Instant, Instant> {
    val iat = parseClaimTimestamp(claims["iat"]) ?: now
    val exp = parseClaimTimestamp(claims["exp"]) ?: (iat + 1.hours)
    return iat to exp
}

internal sealed class FirebaseAuthGatewayException(
    cause: Throwable,
) : Exception(cause) {
    class Cancelled(
        cause: Throwable,
    ) : FirebaseAuthGatewayException(cause)

    class UserCollision(
        cause: Throwable,
    ) : FirebaseAuthGatewayException(cause)

    class RequiresRecentLogin(
        cause: Throwable,
    ) : FirebaseAuthGatewayException(cause)

    class AccountDeletionRemoteFailed(
        cause: Throwable,
    ) : FirebaseAuthGatewayException(cause)

    class Network(
        cause: Throwable,
    ) : FirebaseAuthGatewayException(cause)

    class Provider(
        cause: Throwable,
    ) : FirebaseAuthGatewayException(cause)

    class PermissionDenied(
        cause: Throwable,
    ) : FirebaseAuthGatewayException(cause)

    class Unknown(
        cause: Throwable,
    ) : FirebaseAuthGatewayException(cause)
}

internal fun FirebaseUser.toIntegrationUser(): FirebaseAuthUser =
    FirebaseAuthUser(
        uid = uid,
        isAnonymous = isAnonymous,
        providerIds = providerData.mapTo(mutableSetOf()) { userInfo -> userInfo.providerId },
        createdAt = parseCreationTime(metaData?.creationTime),
    )

private fun FirebaseAuthUser?.toAuthState(): AuthState =
    this?.let { user -> AuthState.SignedIn(user.toSession()) } ?: AuthState.SignedOut

internal fun FirebaseAuthUser.toSession(): AuthSession =
    AuthSession(
        uid = uid,
        isAnonymous = isAnonymous,
        providers =
            buildSet {
                if (isAnonymous) add(AuthProvider.ANONYMOUS)
                if (GOOGLE_PROVIDER_ID in providerIds) add(AuthProvider.GOOGLE)
                if (APPLE_PROVIDER_ID in providerIds) add(AuthProvider.APPLE)
            },
        createdAt = createdAt,
    )

private fun FirebaseAuthGatewayException.toAuthError(): AuthError =
    when (this) {
        is FirebaseAuthGatewayException.Cancelled -> AuthError.Cancelled
        is FirebaseAuthGatewayException.UserCollision -> AuthError.CredentialAlreadyInUse
        is FirebaseAuthGatewayException.RequiresRecentLogin -> AuthError.RequiresRecentLogin
        is FirebaseAuthGatewayException.AccountDeletionRemoteFailed -> AuthError.AccountDeletionRemoteFailed
        is FirebaseAuthGatewayException.PermissionDenied -> AuthError.PermissionDenied
        is FirebaseAuthGatewayException.Network -> AuthError.NetworkUnavailable
        is FirebaseAuthGatewayException.Provider -> AuthError.ProviderUnavailable
        is FirebaseAuthGatewayException.Unknown -> AuthError.Unknown
    }

private const val GOOGLE_PROVIDER_ID = "google.com"
private const val APPLE_PROVIDER_ID = "apple.com"
private const val APPLE_REFERENCE_DATE_OFFSET_SECONDS = 978_307_200.0
private const val EPOCH_SECONDS_CUTOFF = 10_000_000_000L
private const val EPOCH_SECONDS_CUTOFF_DOUBLE = 10_000_000_000.0
private const val MILLIS_PER_SECOND = 1000L
private const val MILLIS_PER_SECOND_DOUBLE = 1000.0
internal const val FIREBASE_COLLISION_CODE = "17025"
internal const val ERROR_CREDENTIAL_ALREADY_IN_USE = "ERROR_CREDENTIAL_ALREADY_IN_USE"
internal const val FIREBASE_RECENT_LOGIN_CODE = "17014"
internal const val ERROR_REQUIRES_RECENT_LOGIN = "ERROR_REQUIRES_RECENT_LOGIN"
internal const val FIREBASE_NETWORK_CODE = "17020"
internal const val ERROR_NETWORK_REQUEST_FAILED = "ERROR_NETWORK_REQUEST_FAILED"
internal const val FIREBASE_WEB_CANCEL_CODE = "17058"
internal const val ERROR_WEB_CONTEXT_CANCELLED = "ERROR_WEB_CONTEXT_CANCELLED"
internal const val FIREBASE_PERMISSION_DENIED_CODE = "17028"
internal const val ERROR_PERMISSION_DENIED = "ERROR_PERMISSION_DENIED"
