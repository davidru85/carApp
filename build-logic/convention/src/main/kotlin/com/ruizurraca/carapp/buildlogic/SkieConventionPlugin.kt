package com.ruizurraca.carapp.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * SKIE, the Kotlin-to-Swift interop layer (`D-58`).
 *
 * `docs/DECISION_BOARD.md` and `AGENTS.md` require SKIE to be applied only to the iOS composition
 * module that owns the exported framework. This plugin fails the build rather than silently
 * applying it elsewhere, so the rule is enforced at the point of use instead of by review.
 */
class SkieConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        check(path == SKIE_ALLOWED_MODULE) {
            "SKIE is applied only to $SKIE_ALLOWED_MODULE (D-58, docs/DECISION_BOARD.md). " +
                "Attempted to apply carapp.skie to $path."
        }
        pluginManager.apply("co.touchlab.skie")
    }

    private companion object {
        const val SKIE_ALLOWED_MODULE = ":composition:ios"
    }
}
