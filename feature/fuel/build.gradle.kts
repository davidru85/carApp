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

    iosArm64 {
        // `E4-03` (`D-255`, ADR-0256): the D-80 run on a real iPhone. The compilation is associated
        // with the test compilation so the unchanged `measureConsumptionBenchmark` procedure stays in
        // commonTest; it contains only the exported entry point, so nothing reaches `Shared` or Android.
        val compilations = this.compilations
        val deviceBenchmark =
            compilations.create("deviceBenchmark") {
                associateWith(compilations.getByName("test"))
            }
        binaries.framework("ConsumptionBenchmark", listOf(NativeBuildType.RELEASE)) {
            compilation = deviceBenchmark
            baseName = "ConsumptionBenchmark"
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
