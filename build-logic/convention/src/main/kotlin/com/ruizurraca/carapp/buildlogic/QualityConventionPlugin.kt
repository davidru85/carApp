package com.ruizurraca.carapp.buildlogic

import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jlleitschuh.gradle.ktlint.KtlintExtension

/**
 * ktlint and detekt for a module (`E0-05`).
 *
 * Both configurations are committed at the repository root and shared by every module, and
 * baseline suppression files are forbidden: a baseline hides existing violations instead of
 * fixing them, and this repository has no legacy code to grandfather.
 */
class QualityConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jlleitschuh.gradle.ktlint")
        pluginManager.apply("io.gitlab.arturbosch.detekt")

        extensions.configure<KtlintExtension> {
            version.set(libs.version("ktlintCli"))
            ignoreFailures.set(false)
            // Generated sources are not ours to format.
            filter {
                exclude { element -> element.file.path.contains("/build/") }
            }
        }

        extensions.configure<DetektExtension> {
            config.setFrom(rootProject.file("detekt.yml"))
            buildUponDefaultConfig = true
            ignoreFailures = false
            // No baseline file: E0-05 forbids baseline suppression.
            source.setFrom(files("src"))
        }
        Unit
    }
}
