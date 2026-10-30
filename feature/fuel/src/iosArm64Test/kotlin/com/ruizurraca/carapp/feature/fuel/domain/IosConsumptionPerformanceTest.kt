package com.ruizurraca.carapp.feature.fuel.domain

import kotlin.test.Test
import kotlin.test.assertFalse

class IosConsumptionPerformanceTest {
    /** `D-80` / `E4-03` (`D-255`): the optimized device binary must meet the `< 100 ms` target. */
    @Test
    fun optimizedDeviceBenchmarkMeetsTheD80Target() {
        val result = measureConsumptionBenchmark(DefaultCalculateConsumption())
        println(
            "E1-05 iOS consumption benchmark: entries=${result.entryCount}, " +
                "warmUps=${result.warmUpRuns}, measuredRuns=${result.measuredRuns}, " +
                "medianNanos=${result.medianNanos}, thresholdNanos=$CONSUMPTION_THRESHOLD_NANOS, " +
                "checksum=${result.checksum}",
        )
        assertFalse(
            exceedsConsumptionThreshold(result.medianNanos),
            "E1-05 consumption median ${result.medianNanos} ns must be below $CONSUMPTION_THRESHOLD_NANOS ns",
        )
    }
}
