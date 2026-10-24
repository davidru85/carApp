package com.ruizurraca.carapp.wiring.firebase

import com.ruizurraca.carapp.AppGraph
import com.ruizurraca.carapp.buildAppGraph
import com.ruizurraca.carapp.core.analytics.AnalyticsEvent
import com.ruizurraca.carapp.core.analytics.AnalyticsTracker
import com.ruizurraca.carapp.core.analytics.AnalyticsUserProperties
import com.ruizurraca.carapp.core.analytics.CountBucket
import com.ruizurraca.carapp.core.auth.AuthClient
import com.ruizurraca.carapp.core.auth.AuthOwnerContext
import com.ruizurraca.carapp.core.auth.AuthSession
import com.ruizurraca.carapp.core.auth.AuthState
import com.ruizurraca.carapp.core.auth.AuthToken
import com.ruizurraca.carapp.core.auth.NativeAuthCredential
import com.ruizurraca.carapp.core.auth.TokenProvider
import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.AuthError
import com.ruizurraca.carapp.core.common.AuthProvider
import com.ruizurraca.carapp.core.common.ConnectivityObserver
import com.ruizurraca.carapp.core.common.EntityType
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.RemoteError
import com.ruizurraca.carapp.core.crash.CrashReporter
import com.ruizurraca.carapp.core.crash.NoOpCrashReporter
import com.ruizurraca.carapp.core.database.createStagedDatabaseFactory
import com.ruizurraca.carapp.core.model.LOCAL_OWNER
import com.ruizurraca.carapp.core.model.OwnerId
import com.ruizurraca.carapp.core.sync.EntitySnapshot
import com.ruizurraca.carapp.core.sync.RemoteAck
import com.ruizurraca.carapp.core.sync.RemoteCursor
import com.ruizurraca.carapp.core.sync.RemotePage
import com.ruizurraca.carapp.core.sync.RemoteSyncSource
import com.ruizurraca.carapp.integration.firebase.analytics.FirebaseAnalyticsTracker
import com.ruizurraca.carapp.integration.firebase.crashlytics.FirebaseCrashReporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame

class FirebaseAppProvidersTest {
    @Test
    fun providerFactoryBuildsTheKotlinGraphWithoutGlobalRegistration() {
        val graph = buildAppGraph(isDebugBuild = true, providers = firebaseAppProviders())

        assertIs<AppGraph>(graph)
        graph.close()
    }

    @Test
    fun providerFactoryKeepsRealBoundariesAndDerivesTheCurrentOwner() {
        val authClient = MutableAuthClient()
        val remoteSyncSource = RecordingRemoteSyncSource()
        val databaseFactory = createStagedDatabaseFactory()

        val providers =
            firebaseAppProviders(
                databaseFactory = databaseFactory,
                authClient = authClient,
                tokenProvider = authClient,
                remoteSyncSource = remoteSyncSource,
            )

        assertSame(databaseFactory, providers.databaseFactory)
        assertSame(authClient, providers.authClient)
        assertSame(remoteSyncSource, providers.remoteSyncSource)
        assertIs<AuthOwnerContext>(providers.ownerContext)
        assertEquals(LOCAL_OWNER, providers.ownerContext.current)

        authClient.state.value =
            AuthState.SignedIn(
                AuthSession(
                    uid = "anonymous-owner",
                    isAnonymous = true,
                    providers = setOf(AuthProvider.ANONYMOUS),
                ),
            )

        assertEquals(OwnerId("anonymous-owner"), providers.ownerContext.current)
    }

    @Test
    fun providerFactoryBindsAuthClientImplementingTokenProviderAsTokenProvider() {
        val authClient = MutableAuthClient()
        val remoteSyncSource = RecordingRemoteSyncSource()
        val databaseFactory = createStagedDatabaseFactory()

        val providers =
            firebaseAppProviders(
                databaseFactory = databaseFactory,
                authClient = authClient,
                tokenProvider = authClient,
                remoteSyncSource = remoteSyncSource,
            )

        assertSame(authClient, providers.authClient)
        assertSame<Any>(authClient, providers.tokenProvider)
    }

    @Test
    fun closingAppGraphClosesAutoCloseableAuthClient() {
        val authClient = MutableAuthClient()
        val remoteSyncSource = RecordingRemoteSyncSource()
        val databaseFactory = createStagedDatabaseFactory()

        val providers =
            firebaseAppProviders(
                databaseFactory = databaseFactory,
                authClient = authClient,
                tokenProvider = authClient,
                remoteSyncSource = remoteSyncSource,
            )
        val graph = buildAppGraph(isDebugBuild = true, providers = providers)

        assertEquals(false, authClient.closed)
        graph.close()
        assertEquals(true, authClient.closed)
    }

    @Test
    fun providerFactoryBindsTheGivenAnalyticsTrackerWithoutDecoratingIt() {
        // `E3-09`: `:wiring:firebase` is the only module that constructs the Firebase Analytics
        // implementation, and it binds whatever tracker it was handed. Asserting identity rather than
        // behaviour is the point: a decorator here would be a second construction site.
        val analyticsTracker = RecordingAnalyticsTrackerForWiring()

        val providers =
            firebaseAppProviders(
                databaseFactory = createStagedDatabaseFactory(),
                authClient = MutableAuthClient(),
                tokenProvider = MutableAuthClient(),
                remoteSyncSource = RecordingRemoteSyncSource(),
                analyticsTracker = analyticsTracker,
            )

        assertSame<Any>(analyticsTracker, providers.analyticsTracker)
    }

    @Test
    fun theInternalFactoryDefaultIsNotTheFirebaseTracker() {
        // The internal factory's default must never be the Firebase implementation: a graph built
        // without a host — a test, or a build with no provider wired — must not reach the provider
        // SDK. The public overload is the only construction site (`D-179`, `D-199`).
        val providers =
            firebaseAppProviders(
                databaseFactory = createStagedDatabaseFactory(),
                authClient = MutableAuthClient(),
                tokenProvider = MutableAuthClient(),
                remoteSyncSource = RecordingRemoteSyncSource(),
            )

        assertFalse(
            providers.analyticsTracker is FirebaseAnalyticsTracker,
            "the internal factory's default must be the private no-op, not the Firebase tracker",
        )
        // The silent default accepts every entry point without throwing.
        providers.analyticsTracker.setEnabled(true)
        providers.analyticsTracker.track(AnalyticsEvent.VehicleCreated)
        providers.analyticsTracker.setUserProperties(
            AnalyticsUserProperties(CountBucket.ONE, CountBucket.ZERO),
        )
    }

    @Test
    fun providerFactoryBindsTheGivenCrashReporterWithoutDecoratingIt() {
        // `E4-04` (`D-199` pattern): `:wiring:firebase` binds whatever reporter it was handed. Identity,
        // not behaviour, is the point: a decorator here would be a second construction site.
        val crashReporter = RecordingCrashReporterForWiring()

        val providers =
            firebaseAppProviders(
                databaseFactory = createStagedDatabaseFactory(),
                authClient = MutableAuthClient(),
                tokenProvider = MutableAuthClient(),
                remoteSyncSource = RecordingRemoteSyncSource(),
                crashReporter = crashReporter,
            )

        assertSame<Any>(crashReporter, providers.crashReporter)
    }

    @Test
    fun theInternalFactoryDefaultIsTheNoOpCrashReporter() {
        // A graph built without a host — a test, or a build with no provider wired — must never reach
        // the provider SDK. The public overload is the only construction site (`D-199` pattern).
        val providers =
            firebaseAppProviders(
                databaseFactory = createStagedDatabaseFactory(),
                authClient = MutableAuthClient(),
                tokenProvider = MutableAuthClient(),
                remoteSyncSource = RecordingRemoteSyncSource(),
            )

        assertFalse(
            providers.crashReporter is FirebaseCrashReporter,
            "the internal default is not the Firebase reporter",
        )
        assertSame<Any>(NoOpCrashReporter, providers.crashReporter)
    }

    @Test
    fun providerFactoryBuildsTheReleaseGraphWithTheGivenCrashReporter() {
        val providers =
            firebaseAppProviders(
                databaseFactory = createStagedDatabaseFactory(),
                authClient = MutableAuthClient(),
                tokenProvider = MutableAuthClient(),
                remoteSyncSource = RecordingRemoteSyncSource(),
                crashReporter = RecordingCrashReporterForWiring(),
            )

        val graph = buildAppGraph(isDebugBuild = false, providers = providers)

        assertIs<AppGraph>(graph)
        graph.close()
    }
}

/**
 * A local analytics recorder. `:wiring:firebase`'s tests deliberately define their own doubles —
 * `MutableAuthClient` and `RecordingRemoteSyncSource` are already here — so the module keeps its
 * dependency surface at what production code needs.
 */
private class RecordingAnalyticsTrackerForWiring : AnalyticsTracker {
    val events = mutableListOf<AnalyticsEvent>()
    var collectionEnabled = false

    override fun track(event: AnalyticsEvent) {
        if (collectionEnabled) events += event
    }

    override fun setUserProperties(properties: AnalyticsUserProperties) = Unit

    // `collectionEnabled`, not `enabled`: a property named `enabled` would clash with the setter of
    // `setEnabled` at the JVM signature level.
    override fun setEnabled(enabled: Boolean) {
        collectionEnabled = enabled
    }
}

/** A local crash recorder, defined here for the same reason as the analytics recorder above. */
private class RecordingCrashReporterForWiring : CrashReporter {
    val errors = mutableListOf<AppError>()

    override fun recordNonFatal(
        error: AppError,
        fields: Map<String, String>,
    ) {
        errors += error
    }

    override fun setEnabled(enabled: Boolean) = Unit
}

private class MutableAuthClient :
    AuthClient,
    TokenProvider,
    AutoCloseable {
    var closed: Boolean = false
    val state = MutableStateFlow<AuthState>(AuthState.SignedOut)
    override val authState = state

    override fun close() {
        closed = true
    }

    override suspend fun signInAnonymously(): Outcome<AuthSession, AuthError> = unavailable()

    override suspend fun signInWithCredential(
        credential: NativeAuthCredential,
        allowUidChange: Boolean,
    ): Outcome<AuthSession, AuthError> = unavailable()

    override suspend fun linkCredential(credential: NativeAuthCredential): Outcome<AuthSession, AuthError> =
        unavailable()

    override suspend fun reauthenticate(credential: NativeAuthCredential): Outcome<AuthSession, AuthError> =
        unavailable()

    override suspend fun signOut(): Outcome<Unit, AuthError> = unavailable()

    override suspend fun deleteAccount(): Outcome<Unit, AuthError> = unavailable()

    override suspend fun getIdToken(forceRefresh: Boolean): Outcome<AuthToken, AuthError> = unavailable()
}

private class RecordingRemoteSyncSource : RemoteSyncSource {
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

private fun <T> unavailable(): Outcome<T, AuthError> = Outcome.Err(AuthError.ProviderUnavailable)
