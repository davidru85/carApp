package com.ruizurraca.carapp.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Jetpack Compose for an Android module. Split from `carapp.android.application` so that a future
 * Android module without Compose does not pay for the compiler plugin.
 */
class ComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

        extensions.configure<ApplicationExtension> {
            buildFeatures {
                compose = true
            }
        }

        dependencies.add(
            "implementation",
            dependencies.platform(libs.findLibrary("compose-bom").get()),
        )
        Unit
    }
}
