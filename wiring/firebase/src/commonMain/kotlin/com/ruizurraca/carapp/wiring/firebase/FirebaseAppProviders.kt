package com.ruizurraca.carapp.wiring.firebase

import com.ruizurraca.carapp.AppProviders
import com.ruizurraca.carapp.core.analytics.AnalyticsEvent
import com.ruizurraca.carapp.core.analytics.AnalyticsTracker
import com.ruizurraca.carapp.core.analytics.AnalyticsUserProperties
import com.ruizurraca.carapp.core.auth.AuthClient
import com.ruizurraca.carapp.core.auth.AuthOwnerContext
import com.ruizurraca.carapp.core.auth.AuthSession
import com.ruizurraca.carapp.core.auth.AuthState
import com.ruizurraca.carapp.core.auth.AuthToken
import com.ruizurraca.carapp.core.auth.NativeAuthCredential
import com.ruizurraca.carapp.core.auth.TokenProvider
import com.ruizurraca.carapp.core.common.AppClock
import com.ruizurraca.carapp.core.common.AuthError
import com.ruizurraca.carapp.core.common.ConnectivityObserver
import com.ruizurraca.carapp.core.common.DispatcherProvider
import com.ruizurraca.carapp.core.common.LocaleInfo
import com.ruizurraca.carapp.core.common.LocaleProvider
import com.ruizurraca.carapp.core.common.LogLevel
import com.ruizurraca.carapp.core.common.Logger
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.RemoteError
import com.ruizurraca.carapp.core.common.SyncTrigger
import com.ruizurraca.carapp.core.common.SyncTriggerAdapter
import com.ruizurraca.carapp.core.common.UuidGenerator
import com.ruizurraca.carapp.core.crash.NoOpCrashReporter
import com.ruizurraca.carapp.core.database.DatabaseFactory
import com.ruizurraca.carapp.core.database.createPersistentDatabaseFactory
import com.ruizurraca.carapp.core.database.createStagedDatabaseFactory
import com.ruizurraca.carapp.core.model.CurrencyCode
import com.ruizurraca.carapp.core.model.OwnerId
import com.ruizurraca.carapp.core.sync.EntitySnapshot
import com.ruizurraca.carapp.core.sync.EntityType
import com.ruizurraca.carapp.core.sync.RemoteAck
import com.ruizurraca.carapp.core.sync.RemoteCursor
import com.ruizurraca.carapp.core.sync.RemotePage
import com.ruizurraca.carapp.core.sync.RemoteSyncSource
import com.ruizurraca.carapp.integration.firebase.auth.FirebaseAuthClient
import com.ruizurraca.carapp.integration.firebase.firestore.FirebaseRemoteSyncSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.random.Random
import kotlin.time.Clock

/**
 * Creates the final provider shape while E0-07 replaces each staged abstraction factory with its
 * real platform or Firebase implementation. No global registration is used (`D-55`, `D-58`).
 */
fun firebaseAppProviders(): AppProviders {
    val authState = MutableStateFlow<AuthState>(AuthState.SignedOut)
    return firebaseAppProviders(
        databaseFactory = createStagedDatabaseFactory(),
        authClient = stagedAuthClient(authState),
        remoteSyncSource = stagedRemoteSyncSource(),
    )
}

/** Creates the production Firebase boundaries for a platform-owned persistent database path. */
fun firebaseAppProviders(
    databaseFilePath: String,
    localeProvider: LocaleProvider,
): AppProviders {
    val authClient = FirebaseAuthClient()
    return firebaseAppProviders(
        databaseFactory = createPersistentDatabaseFactory(databaseFilePath),
        authClient = authClient,
        remoteSyncSource = FirebaseRemoteSyncSource(),
        localeProvider = localeProvider,
    )
}

internal fun firebaseAppProviders(
    databaseFactory: DatabaseFactory,
    authClient: AuthClient,
    remoteSyncSource: RemoteSyncSource,
    localeProvider: LocaleProvider = stagedLocaleProvider(),
): AppProviders {
    val connectivityState = MutableStateFlow(true)

    return object : AppProviders {
        override val databaseFactory = databaseFactory
        override val authClient = authClient
        override val tokenProvider = stagedTokenProvider()
        override val ownerContext = AuthOwnerContext(authClient.authState)
        override val remoteSyncSource = remoteSyncSource
        override val analyticsTracker = stagedAnalyticsTracker()
        override val crashReporter = NoOpCrashReporter
        override val clock = AppClock { Clock.System.now() }
        override val dispatchers = stagedDispatcherProvider()
        override val uuidGenerator = stagedUuidGenerator()
        override val logger = stagedLogger()
        override val localeProvider = localeProvider
        override val connectivityObserver = stagedConnectivityObserver(connectivityState)
        override val syncTriggerAdapter = SyncTriggerAdapter { }
    }
}

private fun stagedAuthClient(authState: StateFlow<AuthState>): AuthClient =
    object : AuthClient {
        override val authState = authState

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

private fun stagedTokenProvider(): TokenProvider =
    object : TokenProvider {
        override suspend fun getIdToken(forceRefresh: Boolean): Outcome<AuthToken, AuthError> = providerUnavailable()
    }

private fun stagedRemoteSyncSource(): RemoteSyncSource =
    object : RemoteSyncSource {
        override suspend fun pushSnapshot(
            ownerId: OwnerId,
            snapshot: EntitySnapshot,
        ): Outcome<RemoteAck, RemoteError> = Outcome.Err(RemoteError.Unavailable)

        override suspend fun pullChanges(
            ownerId: OwnerId,
            entityType: EntityType,
            cursor: RemoteCursor,
            limit: Int,
        ): Outcome<RemotePage, RemoteError> = Outcome.Err(RemoteError.Unavailable)
    }

private fun stagedAnalyticsTracker(): AnalyticsTracker =
    object : AnalyticsTracker {
        override fun track(event: AnalyticsEvent) = Unit

        override fun setUserProperties(properties: AnalyticsUserProperties) = Unit

        override fun setEnabled(enabled: Boolean) = Unit
    }

private fun stagedDispatcherProvider(): DispatcherProvider =
    object : DispatcherProvider {
        override val main: CoroutineDispatcher = Dispatchers.Main
        override val default: CoroutineDispatcher = Dispatchers.Default
        override val io: CoroutineDispatcher = Dispatchers.Default
    }

private fun stagedUuidGenerator(): UuidGenerator =
    UuidGenerator {
        val bytes = Random.nextBytes(UUID_BYTE_COUNT)
        bytes[UUID_VERSION_INDEX] =
            (bytes[UUID_VERSION_INDEX].toInt() and UUID_VERSION_MASK or UUID_VERSION_BITS).toByte()
        bytes[UUID_VARIANT_INDEX] =
            (bytes[UUID_VARIANT_INDEX].toInt() and UUID_VARIANT_MASK or UUID_VARIANT_BITS).toByte()
        bytes.toUuidString()
    }

private fun ByteArray.toUuidString(): String =
    joinToString(separator = "") { byte -> byte.toUByte().toString(UUID_RADIX).padStart(2, '0') }
        .let { value ->
            "${value.substring(UUID_START, UUID_FIRST_GROUP_END)}-" +
                "${value.substring(UUID_FIRST_GROUP_END, UUID_SECOND_GROUP_END)}-" +
                "${value.substring(UUID_SECOND_GROUP_END, UUID_THIRD_GROUP_END)}-" +
                "${value.substring(UUID_THIRD_GROUP_END, UUID_FOURTH_GROUP_END)}-" +
                value.substring(UUID_FOURTH_GROUP_END)
        }

private fun stagedLogger(): Logger =
    object : Logger {
        override fun log(
            level: LogLevel,
            tag: String,
            message: String,
            fields: Map<String, String>,
            throwable: Throwable?,
        ) = Unit
    }

private fun stagedLocaleProvider(): LocaleProvider =
    LocaleProvider {
        LocaleInfo(
            languageTag = "en",
            region = null,
            suggestedCurrency = CurrencyCode("EUR"),
        )
    }

private fun stagedConnectivityObserver(state: StateFlow<Boolean>): ConnectivityObserver =
    object : ConnectivityObserver {
        override val isOnline = state
    }

private fun <T> providerUnavailable(): Outcome<T, AuthError> = Outcome.Err(AuthError.ProviderUnavailable)

private const val UUID_BYTE_COUNT = 16
private const val UUID_RADIX = 16
private const val UUID_VERSION_INDEX = 6
private const val UUID_VARIANT_INDEX = 8
private const val UUID_VERSION_MASK = 0x0f
private const val UUID_VERSION_BITS = 0x40
private const val UUID_VARIANT_MASK = 0x3f
private const val UUID_VARIANT_BITS = 0x80
private const val UUID_START = 0
private const val UUID_FIRST_GROUP_END = 8
private const val UUID_SECOND_GROUP_END = 12
private const val UUID_THIRD_GROUP_END = 16
private const val UUID_FOURTH_GROUP_END = 20
