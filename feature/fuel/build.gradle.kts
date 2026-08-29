import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType

plugins {
    id("carapp.kmp.library")
}

kotlin {
    targets.withType<KotlinNativeTarget>().configureEach {
        if (name == "iosArm64") {
            binaries.test(listOf(NativeBuildType.RELEASE))
        }
    }
}

dependencies {
    "commonMainApi"(projects.core.model)
    "commonMainApi"(projects.core.common)
    "commonMainImplementation"(projects.core.database)
    "commonMainImplementation"(projects.core.sync)
    "commonMainImplementation"(libs.kotlinx.serialization.json)
    "commonTestImplementation"(projects.core.testing)
    "commonTestImplementation"(libs.kotlinx.coroutines.test)
    "commonTestImplementation"(libs.turbine)
}

tasks.register<JavaExec>("consumptionBenchmark") {
    group = "verification"
    description = "Measures E1-05 consumption without test or coverage instrumentation."
    dependsOn("compileAndroidHostTest")
    mainClass.set("com.ruizurraca.carapp.feature.fuel.domain.ConsumptionPerformanceKt")
    classpath(tasks.named<Test>("testAndroidHostTest").get().classpath)
}
