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
import com.ruizurraca.carapp.core.database.DatabaseFactory
import com.ruizurraca.carapp.core.sync.RemoteSyncSource

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
    databaseFactory: DatabaseFactory = missingDefault("databaseFactory"),
    authClient: AuthClient = missingDefault("authClient"),
    tokenProvider: TokenProvider = missingDefault("tokenProvider"),
    ownerContext: OwnerContext = missingDefault("ownerContext"),
    remoteSyncSource: RemoteSyncSource = missingDefault("remoteSyncSource"),
    analyticsTracker: AnalyticsTracker = missingDefault("analyticsTracker"),
    crashReporter: CrashReporter = missingDefault("crashReporter"),
    clock: AppClock = missingDefault("clock"),
    dispatchers: DispatcherProvider = missingDefault("dispatchers"),
    uuidGenerator: UuidGenerator = missingDefault("uuidGenerator"),
    logger: Logger = missingDefault("logger"),
    isDebugBuild: Boolean = true,
    localeProvider: LocaleProvider = missingDefault("localeProvider"),
    connectivityObserver: ConnectivityObserver = missingDefault("connectivityObserver"),
    syncTriggerAdapter: SyncTriggerAdapter = missingDefault("syncTriggerAdapter"),
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

private fun <T> missingDefault(name: String): T = error("Default fake is not implemented: $name")
