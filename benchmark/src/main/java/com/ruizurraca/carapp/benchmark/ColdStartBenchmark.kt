package com.ruizurraca.carapp.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `E4-03` (`D-251`, `D-252`): cold start to first content, median of 10 (`docs/versions-matrix.md`).
 *
 * The gate figure is `timeToFullDisplayMs`, which ends where the app calls `ReportDrawn()`. The waits
 * below only keep each iteration alive until that point; they do not define the figure.
 * Preconditions are in `docs/runbooks/performance-measurement.md`.
 */
@RunWith(AndroidJUnit4::class)
class ColdStartBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    /** The gate: a returning user's populated vehicle list (seeded and adopted history, offline). */
    @Test
    fun returningUserColdStartToPopulatedVehicleList() =
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = CompilationMode.None(),
            startupMode = StartupMode.COLD,
            iterations = COLD_START_ITERATIONS,
            setupBlock = { pressHome() },
        ) {
            startActivityAndWait()
            check(device.wait(Until.hasObject(By.text(BENCHMARK_VEHICLE_NAME)), CONTENT_TIMEOUT_MS)) {
                "The seeded vehicle never appeared: run the seeding and adoption steps of the runbook first."
            }
        }

    /** The second figure: the welcome screen of a signed-out install with an empty database. */
    @Test
    fun signedOutColdStartToWelcomeScreen() =
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = CompilationMode.None(),
            startupMode = StartupMode.COLD,
            iterations = COLD_START_ITERATIONS,
            setupBlock = { pressHome() },
        ) {
            startActivityAndWait()
            check(device.wait(Until.hasObject(By.clickable(true).pkg(TARGET_PACKAGE)), CONTENT_TIMEOUT_MS)) {
                "The welcome screen never appeared: run this test on a fresh install, before seeding."
            }
        }
}
