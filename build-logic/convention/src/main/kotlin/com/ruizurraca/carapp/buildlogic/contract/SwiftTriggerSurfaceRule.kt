package com.ruizurraca.carapp.buildlogic.contract

import com.ruizurraca.carapp.buildlogic.source.KotlinSourceText

/**
 * `docs/CONTRACTS.md §20.10`: `PostWriteDebounce`, `ConnectivityRecovered` and `Periodic` are fired
 * exclusively by platform wiring and MUST NOT be requested from Swift UI code.
 *
 * The prohibition exists for two concrete reasons, not for tidiness. Firing `Periodic` from the UI
 * would duplicate the `WorkManager` / `BGTaskScheduler` arrangement each host already performs, so the
 * cadence would be driven twice; and a UI-layer `requestSync` reaches a different `SyncController`
 * only by bypassing `§9.1`, which permits exactly one. `PostWriteDebounce` has its own owner in the
 * commit path, so a second call site would defeat the coalescing window it exists to apply.
 *
 * The rule is a pure function over source text so it can have a failing fixture. It scans only the iOS
 * platform boundary, because that is the surface `§20.10` names: Kotlin `iosMain` and the Swift host.
 *
 * The banned call is specifically a **`SyncStateHolder`** call site, which is the surface `§20.10`
 * makes executable. Routing the `Periodic` trigger through `AppGraph.syncController()` from platform
 * wiring is the *permitted* route, not a violation: `§9.1` makes that controller the single in-process
 * one, so a `BGTaskScheduler` handler that requests its cycle there is obeying the invariant rather
 * than bypassing it. Banning every `requestSync` would therefore reject the correct implementation.
 *
 * `AppForeground` and `PullToRefresh` are deliberately absent from the banned set. Both are
 * user-initiated or lifecycle-driven and `§20.10` explicitly permits them from the Swift surface.
 */
internal object SwiftTriggerSurfaceRule {
    /** Trigger names that only platform wiring may request. */
    private val PLATFORM_OWNED_TRIGGERS =
        listOf("PostWriteDebounce", "ConnectivityRecovered", "Periodic")

    /** How far past `requestSync` a trigger name may appear and still belong to that call. */
    private const val CALL_WINDOW = 200

    /** How far before `requestSync` its receiver may be written and still belong to that call. */
    private const val RECEIVER_WINDOW = 120

    /**
     * Returns one `"<file>:<line> requests <Trigger>"` entry per offending call site, in file order.
     * Empty means the surface obeys `§20.10`.
     */
    fun violations(sources: Map<String, String>): List<String> =
        sources.entries.flatMap { (path, text) -> violationsIn(path, text) }

    private fun violationsIn(path: String, text: String): List<String> {
        // `KotlinSourceText.code` is the offset-preserving masker the other source rules already use:
        // it blanks comments AND string literals and it handles nested block comments, which are legal
        // in Kotlin and in Swift. A local re-implementation of it drifted from both.
        val code = KotlinSourceText.code(text)
        val found = mutableListOf<String>()
        var index = code.indexOf(REQUEST_SYNC)
        while (index >= 0) {
            // The receiver precedes the call (`syncStateHolder.requestSync`) and the trigger follows
            // it, so the window spans both sides of the call name.
            val window =
                code.substring(
                    maxOf(0, index - RECEIVER_WINDOW),
                    minOf(index + CALL_WINDOW, code.length),
                )
            if (window.contains(STATE_HOLDER, ignoreCase = true)) {
                PLATFORM_OWNED_TRIGGERS
                    .firstOrNull { window.contains(it, ignoreCase = true) }
                    ?.let { found += "$path:${lineOf(code, index)} requests $it" }
            }
            index = code.indexOf(REQUEST_SYNC, index + REQUEST_SYNC.length)
        }
        return found
    }

    private fun lineOf(text: String, offset: Int): Int = text.take(offset).count { it == '\n' } + 1

    private const val REQUEST_SYNC = "requestSync"

    /** The receiver whose platform-owned triggers `§20.10` bans. */
    private const val STATE_HOLDER = "StateHolder"
}
