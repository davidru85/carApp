package com.ruizurraca.carapp.buildlogic

import com.android.build.api.dsl.TestExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

/**
 * The Macrobenchmark `com.android.test` module (`E4-03`, `D-252`).
 *
 * The namespace is derived from the Gradle path like every other module (`D-24`), and the SDK levels
 * come from `gradle/libs.versions.toml`, so the module build script carries neither. The measured
 * application and the build type pairing stay in the module, because they are what it measures.
 */
class AndroidBenchmarkConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.test")
        pluginManager.apply("carapp.quality")

        extensions.configure<KotlinAndroidProjectExtension> {
            jvmToolchain(libs.intVersion("jdk"))
        }

        extensions.configure<TestExtension> {
            namespace = androidNamespace
            compileSdk = libs.intVersion("compileSdk")

            defaultConfig {
                minSdk = libs.intVersion("minSdk")
                targetSdk = libs.intVersion("targetSdk")
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }
        }
    }
}
