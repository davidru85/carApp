package com.ruizurraca.carapp

import com.ruizurraca.carapp.wiring.firebase.firebaseAppProviders
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/** Builds the exported graph with Firebase providers and non-purgeable iOS application storage. */
fun createSwiftAppGraph(isDebugBuild: Boolean): SwiftAppGraph {
    val providers =
        firebaseAppProviders(
            databaseFilePath = iosDatabaseFilePath(),
            localeProvider = IosLocaleProvider(),
        )
    return wrapAppGraphForSwift(
        graph = buildAppGraph(isDebugBuild, providers),
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
