package com.ruizurraca.carapp.feature.fuel.domain

import java.lang.management.ManagementFactory

fun main() {
    val javaAgents =
        ManagementFactory
            .getRuntimeMXBean()
            .inputArguments
            .filter { it.startsWith("-javaagent:") }
    check(javaAgents.isEmpty()) {
        "D-80 requires an uninstrumented benchmark, found: ${javaAgents.joinToString()}"
    }
    val result = measureConsumptionBenchmark(DefaultCalculateConsumption())
    println(
        "E1-05 consumption benchmark: entries=${result.entryCount}, " +
            "warmUps=${result.warmUpRuns}, measuredRuns=${result.measuredRuns}, " +
            "medianNanos=${result.medianNanos}, thresholdNanos=$CONSUMPTION_THRESHOLD_NANOS, " +
            "javaAgents=${javaAgents.size}, checksum=${result.checksum}",
    )
}
