package com.ruizurraca.carapp

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
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

/** Provider-free construction contract for Kotlin platform composition (`CONTRACTS §11.6`). */
@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
@Suppress("LongParameterList")
data class AppGraphDependencies(
    val databaseFactory: DatabaseFactory,
    val authClient: AuthClient,
    val tokenProvider: TokenProvider,
    val ownerContext: OwnerContext,
    val remoteSyncSource: RemoteSyncSource,
    val analyticsTracker: AnalyticsTracker,
    val crashReporter: CrashReporter,
    val clock: AppClock,
    val dispatchers: DispatcherProvider,
    val uuidGenerator: UuidGenerator,
    val logger: Logger,
    val isDebugBuild: Boolean,
    val localeProvider: LocaleProvider,
    val connectivityObserver: ConnectivityObserver,
    val syncTriggerAdapter: SyncTriggerAdapter,
)
