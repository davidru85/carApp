package com.ruizurraca.carapp.buildlogic

import app.cash.sqldelight.gradle.SqlDelightExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * SQLDelight with the AndroidX bundled SQLite adapter selected by `D-36`.
 *
 * The committed `.sq` files are the schema source. Async generation is mandatory because the
 * adapter is suspending, and Native must not link the system SQLite in addition to the bundled
 * implementation.
 */
class SqlDelightConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("app.cash.sqldelight")

        extensions.configure<SqlDelightExtension> {
            linkSqlite.set(false)
            databases.create("AppDatabase") {
                packageName.set("com.ruizurraca.carapp.core.database")
                generateAsync.set(true)
                verifyMigrations.set(true)
                dialect("app.cash.sqldelight:sqlite-3-24-dialect:${libs.version("sqldelight")}")
            }
        }

        dependencies.add("commonMainImplementation", libs.findLibrary("sqldelight-runtime").get())
        dependencies.add("commonMainImplementation", libs.findLibrary("sqldelight-async-extensions").get())
        dependencies.add("commonMainImplementation", libs.findLibrary("sqldelight-coroutines-extensions").get())
        dependencies.add("commonMainImplementation", libs.findLibrary("sqldelight-androidx-driver").get())
        dependencies.add("commonMainImplementation", libs.findLibrary("androidx-sqlite-bundled").get())
        Unit
    }
}
