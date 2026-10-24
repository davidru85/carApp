package com.ruizurraca.carapp.core.testing

import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.crash.CrashReporter

/**
 * A crash reporter that records what its caller offered it, so a test can assert the
 * `docs/CONTRACTS.md §20.3.1` call sites and the `§16.1` consent commands.
 *
 * It has no gate of its own: it records every `recordNonFatal` whatever the last `setEnabled` was,
 * because the call site, not the reporter, is what those tests specify. [enableCommands] keeps every
 * command in order, so the startup command of a fresh install is distinguishable from a reporter that
 * was never told anything.
 */
class RecordingCrashReporter : CrashReporter {
    private val recordedNonFatals = mutableListOf<Pair<AppError, Map<String, String>>>()
    private val recordedEnableCommands = mutableListOf<Boolean>()

    val nonFatals: List<Pair<AppError, Map<String, String>>> get() = recordedNonFatals.toList()
    val enableCommands: List<Boolean> get() = recordedEnableCommands.toList()

    override fun recordNonFatal(
        error: AppError,
        fields: Map<String, String>,
    ) {
        recordedNonFatals += error to fields
    }

    override fun setEnabled(enabled: Boolean) {
        recordedEnableCommands += enabled
    }

    fun clear() {
        recordedNonFatals.clear()
        recordedEnableCommands.clear()
    }
}
