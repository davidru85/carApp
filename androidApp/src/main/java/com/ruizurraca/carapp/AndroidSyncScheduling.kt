package com.ruizurraca.carapp

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ruizurraca.carapp.core.common.SYNC_PERIODIC_INTERVAL_MS
import com.ruizurraca.carapp.core.common.SYNC_WORK
import com.ruizurraca.carapp.core.common.SyncTrigger
import com.ruizurraca.carapp.core.common.SyncTriggerAdapter
import java.util.concurrent.TimeUnit

/**
 * The Android side of the `docs/CONTRACTS.md §9.8` `Periodic` trigger.
 *
 * `§9.1` requires the Android `Periodic` cadence to use
 * `enqueueUniquePeriodicWork(SYNC_WORK, ExistingPeriodicWorkPolicy.KEEP, <periodic request>)` and to
 * route through *the same in-process* `SyncController`. The adapter arranges the cadence; the worker
 * performs the trigger by asking the process graph for its controller, so a background wake-up runs
 * the very controller the UI is using instead of building a second graph (which would also break the
 * single-`DatabaseHandle` rule of `D-89`).
 *
 * `KEEP` is the existing policy, so re-arranging on every graph construction does not restart the
 * interval and cannot postpone the work indefinitely.
 */
internal val androidSyncScheduling: SyncTriggerAdapter =
    SyncTriggerAdapter { reason ->
        if (reason != SyncTrigger.Periodic) return@SyncTriggerAdapter
        val application =
            AndroidAppGraph.applicationOrNull() ?: return@SyncTriggerAdapter
        WorkManager
            .getInstance(application)
            .enqueueUniquePeriodicWork(
                SYNC_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<PeriodicSyncWorker>(
                    SYNC_PERIODIC_INTERVAL_MS,
                    TimeUnit.MILLISECONDS,
                ).build(),
            )
    }

/**
 * Runs a periodic cycle while the worker's execution lease is active.
 *
 * `WorkManager` keeps the process alive only while `doWork()` executes, so reporting success before
 * the cycle finished would let the system reclaim the process mid-cycle and leave outbox rows marked
 * `SYNCING`. The helper is pure so that ordering is testable without `work-testing`: the lease is held
 * for exactly as long as `runSync` runs (`D-187`).
 *
 * A failed cycle is still reported as success. The controller owns persistence, backoff and retry, so
 * a failure is not a worker failure and a WorkManager retry would only duplicate the controller's own
 * schedule.
 */
internal suspend fun runPeriodicWork(runSync: suspend () -> Unit): ListenableWorker.Result {
    runSync()
    return ListenableWorker.Result.success()
}

/**
 * Wakes the process for its `Periodic` sync. It carries no scheduling policy of its own
 * (`docs/DECISION_BOARD.md` "Android background work") and holds no repository or database: it enters
 * the process graph's controller and awaits the cycle there (`D-187`).
 */
class PeriodicSyncWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = runPeriodicWork(AndroidAppGraph::runPeriodicSync)
}
