@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.ruizurraca.carapp.scheduling

import com.ruizurraca.carapp.AppGraph
import com.ruizurraca.carapp.core.common.SYNC_PERIODIC_INTERVAL_MS
import com.ruizurraca.carapp.core.common.SyncTrigger
import com.ruizurraca.carapp.core.common.SyncTriggerAdapter
import platform.BackgroundTasks.BGAppRefreshTaskRequest
import platform.BackgroundTasks.BGTask
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSDate
import platform.Foundation.NSLog
import platform.Foundation.dateWithTimeIntervalSinceNow
import kotlin.concurrent.Volatile

/**
 * The iOS side of the `docs/CONTRACTS.md §9.8` `Periodic` trigger.
 *
 * `§9.1` allows exactly one `BGTaskScheduler` identifier and one in-process `SyncController`. The
 * adapter arranges the cadence under [SYNC_TASK_IDENTIFIER]; the launch handler registered here
 * performs the trigger by requesting the cycle on the `SyncController` of the process graph, so a
 * background wake-up runs the very controller the UI is using instead of building a second graph
 * (which would also break the single-`DatabaseHandle` rule of `D-89`).
 *
 * `BGTaskScheduler` differs from Android's `WorkManager`: it runs each submitted request once and
 * does not repeat it, so the next request exists only because the handler creates it. That asymmetry
 * is why the handler resubmits before it requests the cycle, and why it is recorded here rather than
 * behind the port, which models the arrangement and not the repetition.
 */
internal val iosSyncScheduling: SyncTriggerAdapter =
    SyncTriggerAdapter { reason ->
        if (reason != SyncTrigger.Periodic) return@SyncTriggerAdapter
        IosPeriodicSyncScheduling.submitRequest()
    }

/**
 * Owns the `BGTaskScheduler` registration and the process graph its handler requests cycles on.
 *
 * `registerForTaskWithIdentifier` is a once-per-process call: the system treats a second launch
 * handler for an identifier as an error that terminates the app. [registerHandler] therefore
 * registers on the first graph construction only, while [install] keeps pointing the handler at the
 * newest graph (a test host builds several graphs in one process).
 */
internal object IosPeriodicSyncScheduling {
    /** The process graph, the same instance the UI consumes (`§11.6`, `D-89`). */
    @Volatile
    private var graph: AppGraph? = null

    @Volatile
    private var handlerRegistered = false

    /**
     * Registers the launch handler. Called by `createSwiftAppGraph`, which runs inside the app's
     * launch, because `BGTaskScheduler` requires every launch handler to be registered before the app
     * finishes launching.
     */
    fun registerHandler() {
        if (handlerRegistered) return
        handlerRegistered = true
        val registered =
            BGTaskScheduler.sharedScheduler.registerForTaskWithIdentifier(
                SYNC_TASK_IDENTIFIER,
                null,
            ) { task -> onPeriodicTask(task) }
        // A refused registration means the identifier is absent from
        // `BGTaskSchedulerPermittedIdentifiers`. It is reported rather than retried: retrying would
        // install a second launch handler for the same identifier, which terminates the app.
        if (!registered) {
            NSLog("carApp: the periodic sync task could not be registered")
        }
    }

    /** Points the handler at the graph the process is using. */
    fun install(graph: AppGraph) {
        this.graph = graph
    }

    /**
     * Submits the next `BGAppRefreshTaskRequest`, one `§9.8` interval from now.
     *
     * iOS reads `earliestBeginDate` as a floor, not as an appointment. The same identifier replaces a
     * request that is already pending instead of queueing a second one, so arranging the cadence again
     * - once per graph construction - cannot stack wake-ups.
     */
    fun submitRequest() {
        val request = BGAppRefreshTaskRequest(SYNC_TASK_IDENTIFIER)
        request.earliestBeginDate = NSDate.dateWithTimeIntervalSinceNow(periodicIntervalSeconds())
        if (!BGTaskScheduler.sharedScheduler.submitTaskRequest(request, null)) {
            NSLog("carApp: the periodic sync task could not be submitted")
        }
    }

    /**
     * The launch handler: resubmit the next request, request the cycle on the process graph, and
     * always report the task as completed.
     *
     * `requestSync` is fire-and-forget by `§9.1`: it returns immediately and the cycle it admits is
     * the graph's deferred work, so there is nothing for this handler to wait for and no expiration
     * handler to install. A cycle admitted late is not a task failure, which is the same reading the
     * Android worker takes when it returns success without awaiting its cycle.
     */
    private fun onPeriodicTask(task: BGTask?) {
        submitRequest()
        graph?.syncController()?.requestSync(SyncTrigger.Periodic)
        task?.setTaskCompletedWithSuccess(true)
    }
}

/** The single `BGTaskScheduler` identifier of `§9.1`, which `Info.plist` permits. */
private const val SYNC_TASK_IDENTIFIER = "com.ruizurraca.carapp.sync"

private fun periodicIntervalSeconds(): Double = SYNC_PERIODIC_INTERVAL_MS.toDouble() / MILLIS_PER_SECOND

private const val MILLIS_PER_SECOND = 1_000.0
