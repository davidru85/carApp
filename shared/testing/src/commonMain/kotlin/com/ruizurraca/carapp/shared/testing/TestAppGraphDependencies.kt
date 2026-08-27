package com.ruizurraca.carapp.shared.testing

import com.ruizurraca.carapp.AppGraphDependencies
import com.ruizurraca.carapp.core.analytics.AnalyticsTracker
import com.ruizurraca.carapp.core.auth.AuthClient
import com.ruizurraca.carapp.core.auth.TokenProvider
import com.ruizurraca.carapp.core.common.AppClock
import com.ruizurraca.carapp.core.common.ConnectivityObserver
import com.ruizurraca.carapp.core.common.DispatcherProvider
import com.ruizurraca.carapp.core.common.LocaleProvider
import com.ruizurraca.carapp.core.common.Logger
import com.ruizurraca.carapp.core.common.OwnerContext
import com.ruizurraca.carapp.core.common.SyncTriggerAdapter
import com.ruizurraca.carapp.core.common.UuidGenerator
import com.ruizurraca.carapp.core.crash.CrashReporter
import com.ruizurraca.carapp.core.crash.NoOpCrashReporter
import com.ruizurraca.carapp.core.database.DatabaseFactory
import com.ruizurraca.carapp.core.sync.RemoteSyncSource
import com.ruizurraca.carapp.core.testing.FakeAppClock
import com.ruizurraca.carapp.core.testing.FakeAuthClient
import com.ruizurraca.carapp.core.testing.FakeConnectivityObserver
import com.ruizurraca.carapp.core.testing.FakeLocaleProvider
import com.ruizurraca.carapp.core.testing.FakeOwnerContext
import com.ruizurraca.carapp.core.testing.FakeRemoteSyncSource
import com.ruizurraca.carapp.core.testing.FakeTokenProvider
import com.ruizurraca.carapp.core.testing.FakeUuidGenerator
import com.ruizurraca.carapp.core.testing.InMemoryDatabaseFactory
import com.ruizurraca.carapp.core.testing.NoOpAnalyticsTracker
import com.ruizurraca.carapp.core.testing.RecordingLogger
import com.ruizurraca.carapp.core.testing.RecordingSyncTriggerAdapter
import com.ruizurraca.carapp.core.testing.TestDispatcherProvider

/**
 * Builds the complete provider-free dependency contract for application tests.
 *
 * This test-support declaration intentionally lives in `commonMain`, not `commonTest`. Kotlin
 * Multiplatform modules cannot consume another module's `commonTest` source set, so reusable test
 * support must be production code in its own module and consumers must constrain the dependency
 * to their own `commonTest` configuration (`D-56`).
 */
@Suppress("LongParameterList")
fun testAppGraphDependencies(
    databaseFactory: DatabaseFactory = InMemoryDatabaseFactory(),
    authClient: AuthClient = FakeAuthClient(),
    tokenProvider: TokenProvider = FakeTokenProvider(),
    ownerContext: OwnerContext = FakeOwnerContext(),
    remoteSyncSource: RemoteSyncSource = FakeRemoteSyncSource(),
    analyticsTracker: AnalyticsTracker = NoOpAnalyticsTracker,
    crashReporter: CrashReporter = NoOpCrashReporter,
    clock: AppClock = FakeAppClock(),
    dispatchers: DispatcherProvider = TestDispatcherProvider(),
    uuidGenerator: UuidGenerator = FakeUuidGenerator(),
    logger: Logger = RecordingLogger(),
    isDebugBuild: Boolean = true,
    localeProvider: LocaleProvider = FakeLocaleProvider(),
    connectivityObserver: ConnectivityObserver = FakeConnectivityObserver(),
    syncTriggerAdapter: SyncTriggerAdapter = RecordingSyncTriggerAdapter(),
): AppGraphDependencies =
    AppGraphDependencies(
        databaseFactory = databaseFactory,
        authClient = authClient,
        tokenProvider = tokenProvider,
        ownerContext = ownerContext,
        remoteSyncSource = remoteSyncSource,
        analyticsTracker = analyticsTracker,
        crashReporter = crashReporter,
        clock = clock,
        dispatchers = dispatchers,
        uuidGenerator = uuidGenerator,
        logger = logger,
        isDebugBuild = isDebugBuild,
        localeProvider = localeProvider,
        connectivityObserver = connectivityObserver,
        syncTriggerAdapter = syncTriggerAdapter,
    )
