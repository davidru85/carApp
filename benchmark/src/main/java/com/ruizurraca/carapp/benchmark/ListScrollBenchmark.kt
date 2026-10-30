package com.ruizurraca.carapp.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `E4-03` (`D-252`): frames over 32 ms during a scripted fling across the 1,000-entry list.
 *
 * Each iteration restarts the app, opens the seeded vehicle and flings its fuel entry list from the top
 * until it cannot scroll further. A frame is over 32 ms when `frameOverrunMs` exceeds
 * `(floor(32 / T) - 1) * T` for the device refresh interval `T` (any positive overrun at 60 Hz).
 */
@RunWith(AndroidJUnit4::class)
class ListScrollBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun flingAcrossOneThousandFuelEntries() =
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.None(),
            iterations = SCROLL_ITERATIONS,
            setupBlock = {
                killProcess()
                startActivityAndWait()
                check(device.wait(Until.hasObject(By.text(BENCHMARK_VEHICLE_NAME)), CONTENT_TIMEOUT_MS)) {
                    "The seeded vehicle never appeared: run the seeding and adoption steps of the runbook first."
                }
                device.findObject(By.text(BENCHMARK_VEHICLE_NAME)).click()
                check(device.wait(Until.hasObject(By.scrollable(true)), CONTENT_TIMEOUT_MS)) {
                    "The fuel entry list never appeared."
                }
                device.waitForIdle()
            },
        ) {
            val list = device.findObject(By.scrollable(true))
            list.setGestureMargin(device.displayWidth / GESTURE_MARGIN_DIVISOR)
            var flings = 0
            while (flings < MAX_FLINGS_PER_ITERATION && list.fling(Direction.DOWN)) {
                flings += 1
            }
        }
}
