package com.ruizurraca.carapp.buildlogic

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `E4-03` guards for the performance harness (`D-251` to `D-256`).
 *
 * Every measurement tool of this story lives outside the shipping build, and every seeding entry point
 * must stay out of it. These guards read the committed files, like `FirebaseConfigurationTest`, so a
 * change that moves a tool into a shipping configuration fails the build instead of a store review.
 */
class PerformanceHarnessContractTest {
    private val repositoryRoot = File(checkNotNull(System.getProperty("carapp.repoRoot")))

    private fun read(path: String): String = repositoryRoot.resolve(path).readText()

    private fun exists(path: String): Boolean = repositoryRoot.resolve(path).isFile

    /** The indented YAML block of one `iosApp/project.yml` target or scheme, without its siblings. */
    private fun yamlBlock(
        projectSpec: String,
        name: String,
    ): String =
        projectSpec
            .substringAfter("\n  $name:\n", missingDelimiterValue = "")
            .lines()
            .takeWhile { line -> line.isBlank() || line.startsWith("    ") }
            .joinToString("\n")

    @Test
    fun consumptionBenchmarkFrameworkExportsOnlyTheDeviceEntryPoint() {
        val fuelBuild = read("feature/fuel/build.gradle.kts")

        assertTrue(
            fuelBuild.contains("compilations.create(\"deviceBenchmark\")"),
            "feature/fuel/build.gradle.kts must declare the deviceBenchmark compilation (D-255)",
        )
        assertTrue(fuelBuild.contains("associateWith(compilations.getByName(\"test\"))"))
        assertTrue(fuelBuild.contains("binaries.framework(\"ConsumptionBenchmark\", listOf(NativeBuildType.RELEASE))"))
        assertTrue(exists(CONSUMPTION_ENTRY), "$CONSUMPTION_ENTRY must hold the framework's only entry point")
        val entry = read(CONSUMPTION_ENTRY)
        assertTrue(entry.contains("@ObjCName(name = \"runConsumptionBenchmark\")"))
        assertEquals(1, Regex("""(?m)^fun\s""").findAll(entry).count(), "the framework exports one function")
        assertFalse(Regex("""(?m)^(class|object|interface|val|var|data class)\s""").containsMatchIn(entry))
    }

    @Test
    fun consumptionBenchmarkAppIsADeviceOnlyTargetThatEmbedsOnlyItsFramework() {
        val projectSpec = read("iosApp/project.yml")
        val app = yamlBlock(projectSpec, "ConsumptionBenchmarkApp")

        assertTrue(app.contains("type: application"), "iosApp/project.yml must declare ConsumptionBenchmarkApp")
        assertTrue(app.contains("SUPPORTED_PLATFORMS: iphoneos"))
        assertTrue(app.contains("PRODUCT_BUNDLE_IDENTIFIER: com.ruizurraca.carapp.consumptionbenchmark"))
        assertTrue(app.contains(":feature:fuel:linkConsumptionBenchmarkReleaseFrameworkIosArm64"))
        assertTrue(app.contains("ConsumptionBenchmark.framework"))
        assertFalse(app.contains("embedAndSignAppleFrameworkForXcode"), "the D-80 app never links Shared")
        assertFalse(app.contains("package: Firebase"), "the D-80 app links no Firebase product")
        assertFalse(yamlBlock(projectSpec, "carApp").contains("ConsumptionBenchmark"))
        assertTrue(exists("iosApp/Benchmark/ConsumptionBenchmarkApp.swift"))
        assertTrue(read("iosApp/Benchmark/ConsumptionBenchmarkApp.swift").contains("runConsumptionBenchmark()"))
    }

    private companion object {
        const val CONSUMPTION_ENTRY =
            "feature/fuel/src/iosArm64DeviceBenchmark/kotlin/com/ruizurraca/carapp/feature/fuel/domain/" +
                "ConsumptionBenchmarkEntry.kt"
    }
}
