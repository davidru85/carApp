package com.ruizurraca.carapp.shared.testing

import com.ruizurraca.carapp.AppGraphDependencies
import com.ruizurraca.carapp.AppProviders

/** Builds explicit provider-free graph inputs from the canonical test dependency container. */
fun testAppProviders(dependencies: AppGraphDependencies = testAppGraphDependencies()): AppProviders =
    object : AppProviders {
        override val databaseFactory = dependencies.databaseFactory
        override val authClient = dependencies.authClient
        override val tokenProvider = dependencies.tokenProvider
        override val ownerContext = dependencies.ownerContext
        override val remoteSyncSource = dependencies.remoteSyncSource
        override val analyticsTracker = dependencies.analyticsTracker
        override val crashReporter = dependencies.crashReporter
        override val clock = dependencies.clock
        override val dispatchers = dependencies.dispatchers
        override val uuidGenerator = dependencies.uuidGenerator
        override val logger = dependencies.logger
        override val localeProvider = dependencies.localeProvider
        override val connectivityObserver = dependencies.connectivityObserver
        override val syncTriggerAdapter = dependencies.syncTriggerAdapter
    }
