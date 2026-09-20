package com.ruizurraca.carapp

import android.app.Application
import android.content.pm.ApplicationInfo
import com.ruizurraca.carapp.core.common.SyncTrigger
import com.ruizurraca.carapp.core.sync.SyncController
import com.ruizurraca.carapp.wiring.firebase.firebaseAppProviders
import kotlin.concurrent.Volatile

/**
 * The process-scoped application graph of the Android host.
 *
 * `docs/CONTRACTS.md §9.1` requires every platform trigger to reach *the same in-process*
 * `SyncController`, and a WorkManager worker has no Activity to borrow one from. The graph therefore
 * lives for the process, as it already does on iOS, and the Activity consumes it without closing it:
 * building a second graph inside a worker would violate both the single-controller rule and the
 * single-`DatabaseHandle` rule of `D-89`.
 *
 * Construction is lazy and guarded, so a worker that wakes the process for a periodic sync creates
 * the same single graph any Activity would, exactly once.
 */
internal object AndroidAppGraph {
    @Volatile
    private var installedApplication: Application? = null

    @Volatile
    private var instance: AppGraph? = null

    /** Records the process host. Called once from `CarAppApplication.onCreate`. */
    fun install(application: Application) {
        installedApplication = application
    }

    /** The graph of this process, created on first use and reused by every consumer. */
    fun require(): AppGraph {
        instance?.let { return it }
        return synchronized(this) {
            instance ?: create().also { instance = it }
        }
    }

    /**
     * The process host, or `null` before `CarAppApplication.onCreate` has run.
     *
     * Used by the trigger adapter, which has to arrange platform scheduling from inside graph
     * construction: building the graph recursively at that point would be circular, so the adapter
     * reads the host without forcing the graph.
     */
    fun applicationOrNull(): Application? = installedApplication

    /** The `§9.8` `Periodic` trigger, requested against the graph in force. */
    fun requestPeriodicSync() {
        require().syncController().requestSync(SyncTrigger.Periodic)
    }

    /** The single controller of this process, for a trigger that needs to reason about the cycle. */
    fun syncController(): SyncController = require().syncController()

    /**
     * Discards this process's graph and its database, so the next [require] builds a clean one.
     *
     * This is a **test-only** entry point, and its order is the point: the graph is closed *before* the
     * database file is deleted. Deleting the file under an open connection is the `D-172` hazard —
     * SQLite keeps writing to the unlinked inode — so the file must be removed only once nothing holds
     * it.
     *
     * It exists because the graph is process-scoped (`docs/CONTRACTS.md §9.1`): the instrumented suite
     * shares one process across tests, `CarAppApplication.onCreate` has therefore already built the
     * graph by the time a test starts, and closing the shared graph from a test would leave every later
     * test without one. An instrumented test that needs a clean database calls this instead.
     */
    internal fun resetForTests() {
        synchronized(this) {
            instance?.close()
            instance = null
            installedApplication?.deleteDatabase(DATABASE_FILE_NAME)
        }
    }

    private fun create(): AppGraph {
        val application =
            checkNotNull(installedApplication) {
                "The application graph was requested before CarAppApplication.onCreate installed it"
            }
        return buildAppGraph(
            isDebugBuild = application.isDebuggable(),
            providers =
                firebaseAppProviders(
                    databaseFilePath = application.getDatabasePath(DATABASE_FILE_NAME).absolutePath,
                    localeProvider = AndroidLocaleProvider(),
                    connectivityObserver = AndroidConnectivityObserver.fromSystemService(application),
                    syncTriggerAdapter = androidSyncScheduling,
                ),
        )
    }

    private fun Application.isDebuggable(): Boolean = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
}
