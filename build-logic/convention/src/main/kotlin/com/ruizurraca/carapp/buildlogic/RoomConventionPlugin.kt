package com.ruizurraca.carapp.buildlogic

import androidx.room3.gradle.RoomExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Room 3 KMP (`D-1`) with KSP code generation and the bundled SQLite, for the single module that
 * owns persistence.
 *
 * `exportSchema = true` and the schema directory are set here so that no story can quietly turn
 * schema export off; `fallbackToDestructiveMigration` stays forbidden in every build type
 * (`AGENTS.md`, Technical Rules).
 */
class RoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("androidx.room3")
        pluginManager.apply("com.google.devtools.ksp")

        extensions.configure<RoomExtension> {
            schemaDirectory("${'$'}projectDir/schemas")
        }

        dependencies.add("commonMainImplementation", libs.findLibrary("room-runtime").get())
        dependencies.add("commonMainImplementation", libs.findLibrary("androidx-sqlite-bundled").get())
        listOf("kspAndroid", "kspIosX64", "kspIosArm64", "kspIosSimulatorArm64").forEach { config ->
            dependencies.add(config, libs.findLibrary("room-compiler").get())
        }
    }
}
