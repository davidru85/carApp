@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package com.ruizurraca.carapp

import kotlin.native.HiddenFromObjC

/** `E4-03` (`D-254`): the number of fuel entries the performance measurements need. */
@HiddenFromObjC
const val BENCHMARK_FUEL_ENTRY_COUNT: Int = 1_000

/** `E4-03` (`D-254`): the seeded vehicle's name, which the Android and iOS benchmarks wait for. */
@HiddenFromObjC
const val BENCHMARK_VEHICLE_NAME: String = "Benchmark vehicle"

/** Why a seeding request wrote nothing. */
@HiddenFromObjC
enum class BenchmarkSeedRefusal {
    /** The graph is not the production graph this function knows how to write through. */
    UNSUPPORTED_GRAPH,

    /** The device is online, so a write could trigger anonymous acquisition and remote backup. */
    ONLINE,

    /** The owner is not `LOCAL_OWNER`, so every write would be enqueued for remote backup. */
    NOT_LOCAL_OWNER,
}

/** The outcome of [seedBenchmarkFuelHistory]. */
@HiddenFromObjC
sealed interface BenchmarkSeedResult {
    @HiddenFromObjC
    data class Seeded(
        val vehicleId: String,
        val fuelEntryCount: Int,
    ) : BenchmarkSeedResult

    @HiddenFromObjC
    data class Refused(
        val reason: BenchmarkSeedRefusal,
    ) : BenchmarkSeedResult

    @HiddenFromObjC
    data class Failed(
        val code: String,
        val writtenFuelEntries: Int,
    ) : BenchmarkSeedResult
}

/** One planned fuel entry of the benchmark history. Money is scaled, never floating point. */
@HiddenFromObjC
data class BenchmarkFuelEntrySeed(
    val dateEpochMillis: Long,
    val odometerKm: Long,
    val litersScaled: Long,
    val pricePerLiterScaled: Long,
    val isFullTank: Boolean,
)

/** RED stub: the plan is not implemented yet. */
@HiddenFromObjC
@Suppress("UnusedParameter")
fun benchmarkFuelEntrySeed(index: Int): BenchmarkFuelEntrySeed =
    BenchmarkFuelEntrySeed(
        dateEpochMillis = 0L,
        odometerKm = 0L,
        litersScaled = 0L,
        pricePerLiterScaled = 0L,
        isFullTank = true,
    )

/** RED stub: writes nothing. */
@HiddenFromObjC
@Suppress("UnusedParameter")
suspend fun seedBenchmarkFuelHistory(
    graph: AppGraph,
    fuelEntryCount: Int,
): BenchmarkSeedResult = BenchmarkSeedResult.Refused(BenchmarkSeedRefusal.UNSUPPORTED_GRAPH)
