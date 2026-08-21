package com.ruizurraca.carapp.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

/**
 * The Android host application. Identifiers come from `docs/identifiers.md` and SDK levels from
 * `gradle/libs.versions.toml`, so this plugin invents neither.
 *
 * `:androidApp` is the one module that keeps `com.ruizurraca.carapp` as its Android namespace
 * (`D-24`), because it is the application namespace.
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        // Since AGP 9 Kotlin support is built into AGP, so org.jetbrains.kotlin.android is neither
        // applied nor allowed here.
        pluginManager.apply("com.android.application")
        pluginManager.apply("carapp.quality")

        extensions.configure<KotlinAndroidProjectExtension> {
            jvmToolchain(libs.intVersion("jdk"))
        }

        extensions.configure<ApplicationExtension> {
            namespace = ANDROID_NAMESPACE_ROOT
            compileSdk = libs.intVersion("compileSdk")

            defaultConfig {
                applicationId = ANDROID_NAMESPACE_ROOT
                minSdk = libs.intVersion("minSdk")
                // targetSdk is pinned independently of compileSdk (D-25): compileSdk decides
                // which APIs compile, targetSdk is the runtime contract the app opts into.
                targetSdk = libs.intVersion("targetSdk")
            }
            // Java source/target compatibility is derived from the Kotlin JVM toolchain above,
            // so it is not restated here.
        }
    }
}
