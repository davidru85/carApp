package com.ruizurraca.carapp.feature.fuel.domain

import kotlin.test.Test

class IosConsumptionPerformanceTest {
    @Test
    fun reportsOptimizedDeviceBenchmark() {
        val result = measureConsumptionBenchmark(DefaultCalculateConsumption())
        println(
            "E1-05 iOS consumption benchmark: entries=${result.entryCount}, " +
                "warmUps=${result.warmUpRuns}, measuredRuns=${result.measuredRuns}, " +
                "medianNanos=${result.medianNanos}, thresholdNanos=$CONSUMPTION_THRESHOLD_NANOS, " +
                "checksum=${result.checksum}",
        )
    }
}
