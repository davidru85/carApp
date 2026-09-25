package com.ruizurraca.carapp.buildlogic.contract

import com.ruizurraca.carapp.buildlogic.source.KotlinSourceText

/**
 * `docs/CONTRACTS.md §20.10`: `OwnerChanged`, `PostWriteDebounce`, `ConnectivityRecovered` and
 * `Periodic` are fired exclusively by the app graph or by platform wiring and MUST NOT be requested
 * from Swift UI code.
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
 * The receiver test is an **allowlist**: a `requestSync` call carrying a platform-owned trigger is a
 * violation unless its receiver is the `syncController()` of `§9.1`. Routing the `Periodic` trigger
 * through `AppGraph.syncController()` from platform wiring is the *permitted* route, not a
 * violation, so banning every `requestSync` unconditionally would reject the correct implementation.
 * A denylist of receiver names was the first shape of this rule and a one-line alias evaded it.
 *
 * `AppForeground` and `PullToRefresh` are deliberately absent from the banned set. Both are
 * user-initiated or lifecycle-driven and `§20.10` explicitly permits them from the Swift surface.
 *
 * `OwnerChanged` is owned by `DefaultAppGraph`, which is the only place that observes `OwnerContext`
 * (`D-188`). A Swift call site requesting it would run a recovery cycle the graph never asked for and
 * would bypass the gate that keeps an unrecovered empty list unresolved.
 */
internal object SwiftTriggerSurfaceRule {
    /** Trigger names that only the app graph or platform wiring may request. */
    private val PLATFORM_OWNED_TRIGGERS =
        listOf("OwnerChanged", "PostWriteDebounce", "ConnectivityRecovered", "Periodic")

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
            // The receiver test is an allowlist, not a search for `StateHolder`. A one-line alias
            // (`let holder = model.syncStateHolder`) hides the holder's name from the call site, so a
            // denylist of receiver names is evaded by renaming a local. The permitted route is the
            // one `§9.1` names, and only that one, so anything else carrying a platform-owned trigger
            // is a violation.
            val receiver = code.substring(maxOf(0, index - RECEIVER_WINDOW), index)
            val arguments = code.substring(index, minOf(index + CALL_WINDOW, code.length))
            if (!PERMITTED_RECEIVER.containsMatchIn(receiver)) {
                PLATFORM_OWNED_TRIGGERS
                    .firstOrNull { arguments.contains(it, ignoreCase = true) }
                    ?.let { found += "$path:${lineOf(code, index)} requests $it" }
            }
            index = code.indexOf(REQUEST_SYNC, index + REQUEST_SYNC.length)
        }
        return found
    }

    private fun lineOf(text: String, offset: Int): Int = text.take(offset).count { it == '\n' } + 1

    private const val REQUEST_SYNC = "requestSync"

    /**
     * The only permitted call shape ends in the `syncController()` accessor and its member dot.
     *
     * A substring test was the first shape of this allowlist and an identifier that merely *contains*
     * `syncController` - `syncControllerAlias`, or any unrelated local - passed it while calling a
     * `SyncStateHolder`, which is exactly the `§20.10` violation the rule exists to catch. Matching
     * the member-access shape is what makes the allowlist mean the one route `§9.1` permits.
     */
    private val PERMITTED_RECEIVER =
        Regex("""\bsyncController\s*\(\s*\)\s*\.\s*$""", RegexOption.IGNORE_CASE)
}
