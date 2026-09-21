package com.ruizurraca.carapp

import com.ruizurraca.carapp.connectivity.IosConnectivityObserver
import com.ruizurraca.carapp.locale.IosLocaleProvider
import com.ruizurraca.carapp.scheduling.IosPeriodicSyncScheduling
import com.ruizurraca.carapp.scheduling.iosSyncScheduling
import com.ruizurraca.carapp.wiring.firebase.firebaseAppProviders
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/** Builds the exported graph with Firebase providers and non-purgeable iOS application storage. */
fun createSwiftAppGraph(isDebugBuild: Boolean): SwiftAppGraph {
    // The BGTaskScheduler launch handler is registered here because this function runs inside
    // `@main`'s `init()`, which is within the launch window the platform requires for registration.
    IosPeriodicSyncScheduling.registerHandler()
    val providers =
        firebaseAppProviders(
            databaseFilePath = iosDatabaseFilePath(),
            localeProvider = IosLocaleProvider(),
            connectivityObserver = IosConnectivityObserver.fromNetworkPathMonitor(),
            syncTriggerAdapter = iosSyncScheduling,
        )
    val graph = buildAppGraph(isDebugBuild, providers)
    // The handler requests its cycle on the very graph the UI consumes: `§9.1` permits one
    // `SyncController` per process and `D-89` one `DatabaseHandle`, so a handler that built its own
    // graph would break both.
    IosPeriodicSyncScheduling.install(graph)
    return wrapAppGraphForSwift(
        graph = graph,
        dispatchers = providers.dispatchers,
    )
}

@OptIn(ExperimentalForeignApi::class)
private fun iosDatabaseFilePath(): String {
    val applicationSupportDirectory =
        checkNotNull(
            NSFileManager.defaultManager.URLForDirectory(
                directory = NSApplicationSupportDirectory,
                inDomain = NSUserDomainMask,
                appropriateForURL = null,
                create = true,
                error = null,
            ),
        ) { "The iOS Application Support directory is unavailable" }
    return checkNotNull(applicationSupportDirectory.URLByAppendingPathComponent(DATABASE_FILE_NAME)?.path)
}

private const val DATABASE_FILE_NAME = "carapp.db"
