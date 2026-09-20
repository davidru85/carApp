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
