package com.ruizurraca.carapp.buildlogic

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Every shared Kotlin Multiplatform library module in this repository.
 *
 * Applying `carapp.kmp.library` is enough: it configures the Android and iOS targets, the JDK
 * toolchain, the Android build namespace and the common test dependency, so a new module needs no
 * more than a plugins block (`docs/BACKLOG.md` `E0-02`).
 */
class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        pluginManager.apply("com.android.kotlin.multiplatform.library")
        pluginManager.apply("carapp.quality")
        pluginManager.apply("carapp.coverage")

        extensions.configure<KotlinMultiplatformExtension> {
            jvmToolchain(libs.intVersion("jdk"))

            val androidLibrary = (this as ExtensionAware)
                .extensions.getByName("androidLibrary") as KotlinMultiplatformAndroidLibraryTarget
            androidLibrary.namespace = androidNamespace
            androidLibrary.compileSdk = libs.intVersion("compileSdk")
            androidLibrary.minSdk = libs.intVersion("minSdk")
            // The AGP KMP library plugin creates no host test runner by default; without this the
            // common tests would only ever execute on the Kotlin/Native targets.
            androidLibrary.withHostTest {
                isReturnDefaultValues = true
            }

            iosArm64()
            iosSimulatorArm64()

            sourceSets.getByName("commonMain").dependencies {
                // Every shared module is allowed coroutines (docs/TECHNICAL_PLAN.md §4), and all
                // of them need it, so it is configured once here instead of per module.
                implementation(libs.findLibrary("kotlinx-coroutines-core").get())
            }
            sourceSets.getByName("commonTest").dependencies {
                implementation(libs.findLibrary("kotlin-test").get())
            }
        }

        // Any KMP module can consume :core:database test support. JVM host tests therefore need
        // the JVM artifact even when that consumer does not apply the SQLDelight plugin itself.
        val sqliteVersion = libs.version("sqlite")
        configurations.matching { it.name == "androidHostTestRuntimeClasspath" }.configureEach {
            resolutionStrategy.dependencySubstitution {
                substitute(module("androidx.sqlite:sqlite-bundled:$sqliteVersion"))
                    .using(module("androidx.sqlite:sqlite-bundled-jvm:$sqliteVersion"))
            }
        }
    }
}
