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

    @Test
    fun androidSeedingEntryPointLivesOnlyInTheBenchmarkSourceSet() {
        val benchmarkManifest = "androidApp/src/benchmark/AndroidManifest.xml"
        val seedActivity = "androidApp/src/benchmark/java/com/ruizurraca/carapp/BenchmarkSeedActivity.kt"

        assertTrue(exists(benchmarkManifest), "$benchmarkManifest must declare the seeding activity (D-254)")
        assertTrue(read(benchmarkManifest).contains("android:name=\".BenchmarkSeedActivity\""))
        assertTrue(exists(seedActivity))
        assertTrue(read(seedActivity).contains("seedBenchmarkFuelHistory("))
        listOf("main", "debug", "release", "staging").forEach { sourceSet ->
            repositoryRoot
                .resolve("androidApp/src/$sourceSet")
                .walkTopDown()
                .filter { file -> file.isFile }
                .forEach { file ->
                    val text = file.readText()
                    assertFalse(text.contains("BenchmarkSeedActivity"), "${file.path} names the seeding entry point")
                    assertFalse(text.contains("seedBenchmarkFuelHistory"), "${file.path} calls the seeding function")
                }
        }
    }

    @Test
    fun benchmarkBuildTypeIsStagingPlusItsDeclaredDelta() {
        val androidBuild = read("androidApp/build.gradle.kts")
        val benchmarkBlock = androidBuild.substringAfter("create(\"benchmark\") {", "").substringBefore("\n        }")

        assertTrue(
            benchmarkBlock.contains("initWith(getByName(\"staging\"))"),
            "the benchmark build type must derive from staging (D-252)",
        )
        assertTrue(benchmarkBlock.contains("applicationIdSuffix = \".debug\""))
        assertTrue(benchmarkBlock.contains("signingConfig = signingConfigs.findByName(\"debug\")"))
        assertTrue(benchmarkBlock.contains("matchingFallbacks.clear()"))
        assertTrue(benchmarkBlock.contains("matchingFallbacks += listOf(\"release\")"))
        assertTrue(read("androidApp/src/benchmark/AndroidManifest.xml").contains("<profileable"))
        assertEquals(
            read("androidApp/src/staging/google-services.json"),
            read("androidApp/src/benchmark/google-services.json"),
            "the benchmark build uses exactly the staging Firebase registration",
        )
        assertEquals(
            read("androidApp/src/staging/java/com/ruizurraca/carapp/AppCheckProvider.kt"),
            read("androidApp/src/benchmark/java/com/ruizurraca/carapp/AppCheckProvider.kt"),
            "the benchmark build uses the staging App Check provider",
        )
        assertEquals(
            setOf("AndroidManifest.xml", "AppCheckProvider.kt", "BenchmarkSeedActivity.kt", "google-services.json"),
            repositoryRoot
                .resolve("androidApp/src/benchmark")
                .walkTopDown()
                .filter { file -> file.isFile }
                .map { file -> file.name }
                .toSet(),
            "the benchmark source set holds exactly its declared delta from staging",
        )
        assertTrue(
            read(".gitignore").lines().map(String::trim).contains("!androidApp/src/benchmark/google-services.json"),
        )
    }

    @Test
    fun iosSeedingEntryPointIsCompiledOnlyInTheBenchmarkConfiguration() {
        val support = "iosApp/BenchmarkSupport.swift"
        val projectSpec = read("iosApp/project.yml")

        assertTrue(exists(support), "$support must hold the iOS seeding entry point (D-254)")
        val text = read(support)
        val beforeCondition = text.substringBefore("#if CARAPP_BENCHMARK")
        assertTrue(text.contains("-CARAPP_BENCHMARK_SEED"))
        assertFalse(beforeCondition.contains("-CARAPP_BENCHMARK_SEED"))
        assertFalse(beforeCondition.contains("VehicleFormViewModel"))
        repositoryRoot
            .resolve("iosApp")
            .walkTopDown()
            // Generated output and the device-only test runner (which passes the argument) are not app code.
            .onEnter { directory ->
                directory.name != "build" && directory.name != "PerformanceUITests" &&
                    !directory.name.endsWith(".xcodeproj")
            }.filter { file -> file.isFile && file.extension == "swift" && file.name != "BenchmarkSupport.swift" }
            .forEach { file ->
                assertFalse(file.readText().contains("CARAPP_BENCHMARK_SEED"), "${file.path} reads the seed argument")
            }
        assertTrue(
            projectSpec.contains("  Benchmark: release"),
            "iosApp/project.yml must declare the Benchmark configuration",
        )
        val benchmarkSettings = configBlock(projectSpec, "Benchmark")
        assertTrue(benchmarkSettings.contains("CARAPP_BENCHMARK"))
        assertFalse(
            projectSpec.replace(benchmarkSettings, "").contains("CARAPP_BENCHMARK"),
            "only the Benchmark settings block of the carApp target defines CARAPP_BENCHMARK (D-254)",
        )
    }

    /** The 10-space-indented settings under the first 8-space `<name>:` key (the `carApp` target's configs). */
    private fun configBlock(
        projectSpec: String,
        name: String,
    ): String =
        projectSpec
            .substringAfter("\n        $name:\n", missingDelimiterValue = "")
            .lines()
            .takeWhile { line -> line.startsWith("          ") }
            .joinToString("\n")

    @Test
    fun seedingPlanIsIdenticalOnBothHosts() {
        val kotlin = read("shared/src/commonMain/kotlin/com/ruizurraca/carapp/BenchmarkSeeding.kt")
        val swift = read("iosApp/BenchmarkSupport.swift")

        listOf(
            "\"Benchmark vehicle\"",
            "1_000",
            "10_000",
            "1_577_836_800_000",
            "86_400_000",
            "500",
            "10",
        ).forEach { literal ->
            assertTrue(kotlin.contains(literal), "BenchmarkSeeding.kt lost $literal")
            assertTrue(swift.contains(literal), "BenchmarkSupport.swift does not mirror $literal")
        }
        assertTrue(kotlin.contains("40_000L") && swift.contains("\"40.000\""), "litres differ between hosts")
        assertTrue(kotlin.contains("1_500L") && swift.contains("\"1.500\""), "price differs between hosts")
    }

    @Test
    fun firstContentIsReportedOnBothHosts() {
        val iosList = read("iosApp/VehicleListView.swift")

        assertTrue(read(ANDROID_VEHICLE_LIST).contains("ReportDrawn()"), "the populated list must report full display (D-251)")
        assertTrue(
            read("androidApp/src/main/java/com/ruizurraca/carapp/AndroidOnboarding.kt").contains("ReportDrawn()"),
            "the welcome screen must report full display (D-251)",
        )
        assertTrue(read("iosApp/carAppApp.swift").contains("LaunchMeasurement.shared.begin()"))
        assertTrue(read("iosApp/WelcomeView.swift").contains("LaunchMeasurement.shared.finishAtFirstContent()"))
        assertEquals(
            3,
            Regex("""LaunchMeasurement\.shared\.finishAtFirstContent\(\)""").findAll(iosList).count(),
            "the first vehicle row, the empty list and the unreadable list each end the launch task",
        )
    }

    @Test
    fun benchmarkModuleMeasuresTheBenchmarkVariantOfTheApp() {
        assertTrue(
            read("settings.gradle.kts").contains("include(\":benchmark\")"),
            "settings.gradle.kts must include :benchmark (D-252)",
        )
        assertTrue(exists("benchmark/build.gradle.kts"))
        val build = read("benchmark/build.gradle.kts")
        assertTrue(build.contains("id(\"carapp.android.benchmark\")"))
        assertTrue(build.contains("targetProjectPath = \":androidApp\""))
        assertTrue(build.contains("experimentalProperties[\"android.experimental.self-instrumenting\"] = true"))
        assertTrue(build.contains("create(\"benchmark\")"))
        assertFalse(build.contains("project("), "the benchmark module depends on no project module")
    }

    @Test
    fun macrobenchmarksWaitForTheSeededVehicleOfTheBenchmarkApplication() {
        val targets = "benchmark/src/main/java/com/ruizurraca/carapp/benchmark/BenchmarkTargets.kt"

        assertTrue(exists(targets), "$targets must name the measured application (D-252)")
        assertTrue(read(targets).contains("const val TARGET_PACKAGE = \"com.ruizurraca.carapp.debug\""))
        assertTrue(read(targets).contains("const val BENCHMARK_VEHICLE_NAME = \"Benchmark vehicle\""))
        assertTrue(
            read("shared/src/commonMain/kotlin/com/ruizurraca/carapp/BenchmarkSeeding.kt")
                .contains("const val BENCHMARK_VEHICLE_NAME: String = \"Benchmark vehicle\""),
        )
    }

    @Test
    fun iosPerformanceTestsRunOnlyOnADeviceAgainstTheBenchmarkConfiguration() {
        val projectSpec = read("iosApp/project.yml")
        val target = yamlBlock(projectSpec, "carAppPerformanceUITests")
        val scheme = yamlBlock(projectSpec, "carAppPerformance")
        val shippingScheme = yamlBlock(projectSpec.substringAfter("\nschemes:"), "carApp")

        assertTrue(target.contains("type: bundle.ui-testing"), "iosApp/project.yml must declare carAppPerformanceUITests (D-253)")
        assertTrue(target.contains("SUPPORTED_PLATFORMS: iphoneos"))
        assertTrue(target.contains("PRODUCT_BUNDLE_IDENTIFIER: com.ruizurraca.carapp.perfuitests"))
        assertTrue(target.contains("TEST_TARGET_NAME: carApp"))
        assertTrue(scheme.contains("config: Benchmark"))
        assertTrue(scheme.contains("carAppPerformanceUITests"))
        assertFalse(shippingScheme.contains("carAppPerformanceUITests"), "the CI scheme never runs device tests")
        assertTrue(
            read("iosApp/PerformanceUITests/LaunchPerformanceUITests.swift")
                .contains("XCTApplicationLaunchMetric(waitUntilResponsive: false)"),
        )
        assertTrue(read("iosApp/PerformanceUITests/ListScrollPerformanceUITests.swift").contains("XCTHitchMetric(application: app)"))
        assertTrue(read("iosApp/PerformanceUITests/PerformanceUITestSupport.swift").contains("\"Benchmark vehicle\""))
    }

    private companion object {
        const val ANDROID_VEHICLE_LIST = "androidApp/src/main/java/com/ruizurraca/carapp/MainActivity.kt"
        const val CONSUMPTION_ENTRY =
            "feature/fuel/src/iosArm64DeviceBenchmark/kotlin/com/ruizurraca/carapp/feature/fuel/domain/" +
                "ConsumptionBenchmarkEntry.kt"
    }
}
