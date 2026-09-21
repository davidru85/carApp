package com.ruizurraca.carapp

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
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
 * Wakes the process for its `Periodic` sync. It carries no scheduling policy of its own
 * (`docs/DECISION_BOARD.md` "Android background work"): it requests the cycle and reports success,
 * because a deferred cycle is not a worker failure and a retry would only duplicate it.
 */
class PeriodicSyncWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        AndroidAppGraph.requestPeriodicSync()
        return Result.success()
    }
}
