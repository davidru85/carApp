package com.ruizurraca.carapp.buildlogic

import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Kover coverage with the per-module thresholds of `D-18`, recorded in
 * `docs/versions-matrix.md` under "Coverage thresholds".
 *
 * A module with no threshold still produces a report; it just does not fail the build. The
 * thresholds are declared here rather than in each module so that a new module cannot quietly
 * opt out of the one that applies to it.
 */
class CoverageConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlinx.kover")

        val threshold = THRESHOLDS[path] ?: FEATURE_DOMAIN_THRESHOLD.takeIf { path.startsWith(":feature:") }

        if (threshold != null) {
            extensions.configure<KoverProjectExtension> {
                reports {
                    verify {
                        rule {
                            minBound(threshold)
                        }
                    }
                }
            }
        }
        Unit
    }

    private companion object {
        /** `docs/versions-matrix.md`, "Coverage thresholds" (`D-18`). */
        val THRESHOLDS = mapOf(
            ":core:model" to 90,
            ":core:common" to 90,
            ":core:sync" to 80,
        )

        /** Feature `domain` packages are held to 85%; the module is the closest enforceable unit. */
        const val FEATURE_DOMAIN_THRESHOLD = 85
    }
}
