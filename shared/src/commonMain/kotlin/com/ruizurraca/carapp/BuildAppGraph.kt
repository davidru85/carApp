package com.ruizurraca.carapp

import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

/** Builds the Kotlin graph without linking any concrete provider module (`D-58`, `D-59`, `D-86`). */
@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
fun buildAppGraph(
    isDebugBuild: Boolean,
    providers: AppProviders,
): AppGraph =
    DefaultAppGraph(
        AppGraphDependencies(
            databaseFactory = providers.databaseFactory,
            authClient = providers.authClient,
            tokenProvider = providers.tokenProvider,
            ownerContext = providers.ownerContext,
            remoteSyncSource = providers.remoteSyncSource,
            analyticsTracker = providers.analyticsTracker,
            crashReporter = providers.crashReporter,
            clock = providers.clock,
            dispatchers = providers.dispatchers,
            uuidGenerator = providers.uuidGenerator,
            logger = providers.logger,
            isDebugBuild = isDebugBuild,
            localeProvider = providers.localeProvider,
            connectivityObserver = providers.connectivityObserver,
            syncTriggerAdapter = providers.syncTriggerAdapter,
        ),
    )
