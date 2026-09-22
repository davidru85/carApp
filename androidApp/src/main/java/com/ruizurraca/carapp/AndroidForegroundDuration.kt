package com.ruizurraca.carapp

import android.os.SystemClock

/**
 * Measures how long the Android app spent in the background, for the `§9.8` foreground trigger.
 *
 * The elapsed time is read from a monotonic source, so a wall-clock change while the app is in the
 * background cannot produce a negative or wildly wrong duration. [elapsedRealtimeMillis] is injected
 * so the arithmetic is exercised by a unit test rather than only on a device; production passes
 * [SystemClock.elapsedRealtime].
 *
 * `null` is the cold start: the first foreground entry has no preceding background stay, and `§9.8`
 * names the cold start as a trigger in its own right.
 */
internal class AndroidForegroundDuration(
    private val elapsedRealtimeMillis: () -> Long = SystemClock::elapsedRealtime,
) {
    private var backgroundedAtMillis: Long? = null

    /** Records when the app left the foreground. A repeated call keeps the earliest moment. */
    fun onBackgrounded() {
        if (backgroundedAtMillis == null) backgroundedAtMillis = elapsedRealtimeMillis()
    }

    /**
     * Reports the completed background stay and clears it, or `null` when there is none to report.
     * The measurement describes one transition and is never reused.
     */
    fun onForegrounded(): Long? {
        val backgroundedAt = backgroundedAtMillis ?: return null
        backgroundedAtMillis = null
        return (elapsedRealtimeMillis() - backgroundedAt).coerceAtLeast(0L)
    }
}

/**
 * The process-scoped [AndroidForegroundDuration] of this host.
 *
 * The measurement MUST outlive the Activity and its composition. `docs/CONTRACTS.md §9.8` fires the
 * foreground trigger on a cold start and on a resume after more than `FOREGROUND_RESUME_THRESHOLD_MS`
 * in the background, and on nothing else. A tracker owned by the composition is discarded whenever
 * the Activity is recreated - `AndroidManifest.xml` deliberately leaves `locale` out of
 * `configChanges`, so a language change recreates it - and `LifecycleRegistry` re-dispatches
 * `ON_START` to the observer the new composition adds. The reading would then be `null`, which
 * `SyncStateHolder.onForegroundReturn` reads as a cold start, and the host would request a cycle
 * `§9.8` does not permit.
 *
 * Holding it for the process is also what makes the two hosts agree: on iOS `SceneBackgroundDuration`
 * is a stored property of the `App` value and already lives for the process.
 *
 * It is touched only from the main thread, by `Lifecycle` callbacks, so it needs no synchronisation.
 */
internal object AndroidForegroundTracking {
    val duration = AndroidForegroundDuration()
}
