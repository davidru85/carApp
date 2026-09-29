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
import com.ruizurraca.carapp.core.common.AuthError
import com.ruizurraca.carapp.core.common.AuthProvider
import com.ruizurraca.carapp.core.common.ConnectivityObserver
import com.ruizurraca.carapp.core.common.LocaleInfo
import com.ruizurraca.carapp.core.common.LocaleProvider
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.RemoteError
import com.ruizurraca.carapp.core.database.createStagedDatabaseFactory
import com.ruizurraca.carapp.core.model.CurrencyCode
import com.ruizurraca.carapp.core.model.LOCAL_OWNER
import com.ruizurraca.carapp.core.model.OwnerId
import com.ruizurraca.carapp.core.sync.EntitySnapshot
import com.ruizurraca.carapp.core.sync.EntityType
import com.ruizurraca.carapp.core.sync.RemoteAck
import com.ruizurraca.carapp.core.sync.RemoteCursor
import com.ruizurraca.carapp.core.sync.RemotePage
import com.ruizurraca.carapp.core.sync.RemoteSyncSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun theStagedGraphWithoutAHostCollectsNothing() {
        // The internal factory's default is the no-op, so a graph built without a host — a test, or a
        // build with no provider wired — cannot collect. The assertion is behavioural rather than an
        // identity check, so it stays honest if the silent default is ever replaced by another.
        val providers =
            firebaseAppProviders(
                databaseFactory = createStagedDatabaseFactory(),
                authClient = MutableAuthClient(),
                tokenProvider = MutableAuthClient(),
                remoteSyncSource = RecordingRemoteSyncSource(),
            )

        providers.analyticsTracker.setEnabled(true)
        providers.analyticsTracker.track(AnalyticsEvent.VehicleCreated)
        providers.analyticsTracker.setUserProperties(
            AnalyticsUserProperties(CountBucket.ONE, CountBucket.ZERO),
        )
    }

    @Test
    fun theProductionFactoryThreadsItsAnalyticsTrackerIntoTheGraph() {
        // The production entry point is where the Firebase implementation is constructed, and its
        // tracker MUST reach the graph's `AppGraphDependencies`, which is the only path by which the
        // shared orchestration can emit anything.
        val analyticsTracker = RecordingAnalyticsTrackerForWiring()

        val providers =
            firebaseAppProviders(
                databaseFilePath = "/tmp/carapp-e3-09-provider-shape-test.db",
                localeProvider =
                    LocaleProvider {
                        LocaleInfo(
                            languageTag = "en",
                            region = null,
                            suggestedCurrency = CurrencyCode("EUR"),
                        )
                    },
                connectivityObserver =
                    object : ConnectivityObserver {
                        override val isOnline = MutableStateFlow(false)
                    },
                analyticsTracker = analyticsTracker,
            )

        assertSame<Any>(analyticsTracker, providers.analyticsTracker)

        val graph = buildAppGraph(isDebugBuild = true, providers = providers)
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
    var enabled = false

    override fun track(event: AnalyticsEvent) {
        if (enabled) events += event
    }

    override fun setUserProperties(properties: AnalyticsUserProperties) = Unit

    override fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }
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
