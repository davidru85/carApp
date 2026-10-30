@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.ConnectivityObserver
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.OwnerContext
import com.ruizurraca.carapp.core.model.CurrencyCode
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.FuelType
import com.ruizurraca.carapp.core.model.LOCAL_OWNER
import com.ruizurraca.carapp.feature.fuel.domain.CreateFuelEntryCommand
import com.ruizurraca.carapp.feature.fuel.domain.FuelEntryRepository
import com.ruizurraca.carapp.feature.fuel.domain.MoneyInput
import com.ruizurraca.carapp.feature.vehicle.domain.CreateVehicleCommand
import kotlin.native.HiddenFromObjC
import kotlin.time.Instant

/** `E4-03` (`D-254`): the number of fuel entries the performance measurements need. */
@HiddenFromObjC
const val BENCHMARK_FUEL_ENTRY_COUNT: Int = 1_000

/** `E4-03` (`D-254`): the seeded vehicle's name, which the Android and iOS benchmarks wait for. */
@HiddenFromObjC
const val BENCHMARK_VEHICLE_NAME: String = "Benchmark vehicle"

/** The seeded vehicle's initial odometer. */
internal const val BENCHMARK_VEHICLE_INITIAL_ODOMETER_KM = 10_000L

/** The first entry's date, 2020-01-01T00:00:00Z: inside the `D-81` window of any vehicle created after 2006. */
internal const val BENCHMARK_FIRST_ENTRY_EPOCH_MILLIS = 1_577_836_800_000L

/** One day between consecutive entries. */
internal const val BENCHMARK_ENTRY_SPACING_MILLIS = 86_400_000L

/** Each entry advances the odometer by this many kilometres, so no `R-1` warning is ever raised. */
internal const val BENCHMARK_ODOMETER_STEP_KM = 500L

/** 40.000 litres, scaled by 1,000. */
internal const val BENCHMARK_LITERS_SCALED = 40_000L

/** 1.500 per litre, scaled by 1,000. */
internal const val BENCHMARK_PRICE_PER_LITER_SCALED = 1_500L

/** Every tenth entry is a partial fill, so the list renders both row shapes. */
internal const val BENCHMARK_PARTIAL_FILL_PERIOD = 10

/** A supported currency with two minor digits. */
internal const val BENCHMARK_CURRENCY = "EUR"

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

/**
 * The deterministic plan of entry [index] (from 0). It depends on nothing but [index], so every seeded
 * device holds the same history; `iosApp/BenchmarkSupport.swift` mirrors these constants.
 */
@HiddenFromObjC
fun benchmarkFuelEntrySeed(index: Int): BenchmarkFuelEntrySeed =
    BenchmarkFuelEntrySeed(
        dateEpochMillis = BENCHMARK_FIRST_ENTRY_EPOCH_MILLIS + index * BENCHMARK_ENTRY_SPACING_MILLIS,
        odometerKm = BENCHMARK_VEHICLE_INITIAL_ODOMETER_KM + (index + 1) * BENCHMARK_ODOMETER_STEP_KM,
        litersScaled = BENCHMARK_LITERS_SCALED,
        pricePerLiterScaled = BENCHMARK_PRICE_PER_LITER_SCALED,
        isFullTank = index % BENCHMARK_PARTIAL_FILL_PERIOD != BENCHMARK_PARTIAL_FILL_PERIOD - 1,
    )

/**
 * `E4-03` (`D-254`, ADR-0255): writes one vehicle named [BENCHMARK_VEHICLE_NAME] and [fuelEntryCount]
 * planned entries through [graph]'s own decorated repositories, so `DatabaseMutations`, validation and
 * the read models run exactly as for the owner's data.
 *
 * It writes only while the device is offline and the owner is `LOCAL_OWNER` - the state in which
 * nothing is enqueued for remote backup (`docs/SPECIFICATION.md §9.1`) - and re-checks both before
 * every write. It is reached only from the benchmark entry points; no shipping code calls it.
 */
@HiddenFromObjC
suspend fun seedBenchmarkFuelHistory(
    graph: AppGraph,
    fuelEntryCount: Int,
): BenchmarkSeedResult {
    val productionGraph =
        graph as? DefaultAppGraph ?: return BenchmarkSeedResult.Refused(BenchmarkSeedRefusal.UNSUPPORTED_GRAPH)
    return productionGraph.benchmarkFuelHistorySeeder().seed(fuelEntryCount)
}

internal class BenchmarkFuelHistorySeeder(
    private val ownerContext: OwnerContext,
    private val connectivity: ConnectivityObserver,
    private val createVehicle: suspend (CreateVehicleCommand) -> Outcome<EntityId, AppError>,
    private val fuelRepository: FuelEntryRepository,
) {
    suspend fun seed(fuelEntryCount: Int): BenchmarkSeedResult {
        refusal()?.let { reason -> return BenchmarkSeedResult.Refused(reason) }
        return when (val vehicle = createVehicle(benchmarkVehicleCommand())) {
            is Outcome.Err -> BenchmarkSeedResult.Failed(vehicle.error.code, writtenFuelEntries = 0)
            is Outcome.Ok -> writeEntries(vehicle.value, fuelEntryCount)
        }
    }

    private suspend fun writeEntries(
        vehicleId: EntityId,
        fuelEntryCount: Int,
    ): BenchmarkSeedResult {
        for (index in 0 until fuelEntryCount) {
            // Re-checked before every write: an owner or a network that changed mid-run would turn the
            // next write into an enqueued one.
            refusal()?.let { reason -> return BenchmarkSeedResult.Refused(reason) }
            val written = fuelRepository.createFuelEntry(benchmarkFuelEntryCommand(vehicleId, index))
            if (written is Outcome.Err) return BenchmarkSeedResult.Failed(written.error.code, index)
        }
        return BenchmarkSeedResult.Seeded(vehicleId.value, fuelEntryCount)
    }

    private fun refusal(): BenchmarkSeedRefusal? =
        when {
            connectivity.isOnline.value -> BenchmarkSeedRefusal.ONLINE
            ownerContext.current != LOCAL_OWNER -> BenchmarkSeedRefusal.NOT_LOCAL_OWNER
            else -> null
        }
}

private fun benchmarkVehicleCommand(): CreateVehicleCommand =
    CreateVehicleCommand(
        name = BENCHMARK_VEHICLE_NAME,
        initialOdometerKm = BENCHMARK_VEHICLE_INITIAL_ODOMETER_KM,
        brand = null,
        model = null,
        fuelType = FuelType.GASOLINE,
        confirmations = emptySet(),
    )

private fun benchmarkFuelEntryCommand(
    vehicleId: EntityId,
    index: Int,
): CreateFuelEntryCommand {
    val seed = benchmarkFuelEntrySeed(index)
    return CreateFuelEntryCommand(
        vehicleId = vehicleId,
        date = Instant.fromEpochMilliseconds(seed.dateEpochMillis),
        odometerKm = seed.odometerKm,
        money = MoneyInput.LitersAndPrice(seed.litersScaled, seed.pricePerLiterScaled),
        currency = CurrencyCode(BENCHMARK_CURRENCY),
        isFullTank = seed.isFullTank,
        hasMissedEntries = false,
        notes = null,
        confirmations = emptySet(),
    )
}
