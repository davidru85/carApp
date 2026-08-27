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

/** Explicit provider boundary consumed by the provider-free graph factory (`D-59`). */
@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
interface AppProviders {
    val databaseFactory: DatabaseFactory
    val authClient: AuthClient
    val tokenProvider: TokenProvider
    val ownerContext: OwnerContext
    val remoteSyncSource: RemoteSyncSource
    val analyticsTracker: AnalyticsTracker
    val crashReporter: CrashReporter
    val clock: AppClock
    val dispatchers: DispatcherProvider
    val uuidGenerator: UuidGenerator
    val logger: Logger
    val localeProvider: LocaleProvider
    val connectivityObserver: ConnectivityObserver
    val syncTriggerAdapter: SyncTriggerAdapter
}
