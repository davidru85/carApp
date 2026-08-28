package com.ruizurraca.carapp.feature.fuel.domain

import com.ruizurraca.carapp.core.model.FuelEntry
import kotlin.time.TimeSource

internal const val BENCHMARK_ENTRY_COUNT = 1_000
internal const val BENCHMARK_WARM_UP_RUNS = 5
internal const val BENCHMARK_MEASURED_RUNS = 20
internal const val CONSUMPTION_THRESHOLD_NANOS = 100_000_000L

internal data class ConsumptionBenchmarkResult(
    val medianNanos: Long,
    val measuredRuns: Int,
    val warmUpRuns: Int,
    val entryCount: Int,
    val checksum: Long,
)

internal fun consumptionBenchmarkEntries(): List<FuelEntry> =
    List(BENCHMARK_ENTRY_COUNT) { index ->
        consumptionEntry(
            idNumber = index + 1,
            dateMillis = index.toLong() * 1_000L,
            odometerKm = index.toLong() * 500L,
            litersScaled = 40_000L,
        )
    }.reversed()

internal fun measureConsumptionBenchmark(
    calculate: CalculateConsumption,
    entries: List<FuelEntry> = consumptionBenchmarkEntries(),
): ConsumptionBenchmarkResult {
    var checksum = 0L
    repeat(BENCHMARK_WARM_UP_RUNS) {
        checksum += calculate(entries).validSegmentCount
    }
    val samples =
        List(BENCHMARK_MEASURED_RUNS) {
            val mark = TimeSource.Monotonic.markNow()
            checksum += calculate(entries).validSegmentCount
            mark.elapsedNow().inWholeNanoseconds
        }.sorted()
    val median = (samples[9] + samples[10]) / 2L
    return ConsumptionBenchmarkResult(
        medianNanos = median,
        measuredRuns = samples.size,
        warmUpRuns = BENCHMARK_WARM_UP_RUNS,
        entryCount = entries.size,
        checksum = checksum,
    )
}
