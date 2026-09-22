@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.ruizurraca.carapp.scheduling

import com.ruizurraca.carapp.AppGraph
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.SYNC_PERIODIC_INTERVAL_MS
import com.ruizurraca.carapp.core.common.SyncTrigger
import com.ruizurraca.carapp.core.common.SyncTriggerAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import platform.BackgroundTasks.BGAppRefreshTaskRequest
import platform.BackgroundTasks.BGTask
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSDate
import platform.Foundation.NSLock
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

    // Owned by the process, not by the task: the periodic cycle outlives the handler call that
    // started it, and cancelling this scope cancels an overrunning cycle when the task expires.
    private val taskScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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
     * The launch handler: resubmit the next request, then run the cycle and report the task completed
     * only once it has finished.
     *
     * `setTaskCompletedWithSuccess` tells iOS the task has ended and the system may then suspend the
     * process, so completing before the cycle finished would cut it off mid-flight. The handler
     * therefore awaits `sync(SyncTrigger.Periodic)` on the one process graph (`D-187`), and installs an
     * expiration handler that cancels an overrunning cycle and reports the task completed as a
     * failure. A sync `Outcome.Err` is not converted into anything: the controller owns persistence,
     * backoff and retry, and the lease exists to keep the process runnable.
     */
    private fun onPeriodicTask(task: BGTask?) {
        submitRequest()
        val backgroundTask = task ?: return
        val completion = BackgroundTaskCompletion(backgroundTask)
        val syncJob =
            taskScope.launch(start = CoroutineStart.LAZY) {
                val outcome = graph?.syncController()?.sync(SyncTrigger.Periodic)
                completion.complete(outcome is Outcome.Ok<*>)
            }
        // Installed before the job starts, so an expiry that arrives immediately after launch cannot
        // leave the task with nothing to complete it.
        backgroundTask.expirationHandler = {
            syncJob.cancel()
            completion.complete(success = false)
        }
        syncJob.start()
    }
}

/**
 * Completes a `BGTask` at most once, whichever of the two paths arrives first.
 *
 * `setTaskCompletedWithSuccess` must be called exactly once per task: a second call is a programming
 * error, and the expiry handler and the sync job race by construction, so the winner is decided under
 * a lock rather than by ordering.
 */
private class BackgroundTaskCompletion(
    private val task: BGTask,
) {
    private val lock = NSLock()
    private var completed = false

    fun complete(success: Boolean) {
        lock.lock()
        val shouldComplete = !completed
        if (shouldComplete) completed = true
        lock.unlock()
        if (shouldComplete) task.setTaskCompletedWithSuccess(success)
    }
}

/** The single `BGTaskScheduler` identifier of `§9.1`, which `Info.plist` permits. */
private const val SYNC_TASK_IDENTIFIER = "com.ruizurraca.carapp.sync"

private fun periodicIntervalSeconds(): Double = SYNC_PERIODIC_INTERVAL_MS.toDouble() / MILLIS_PER_SECOND

private const val MILLIS_PER_SECOND = 1_000.0
