package com.ruizurraca.carapp

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * `E4-03` (`D-254`, ADR-0255): the benchmark-only seeding entry point of the Android host.
 *
 * It exists only in the `benchmark` source set. It writes the benchmark history through the
 * process-scoped graph and logs one line with the [BENCHMARK_LOG_TAG] tag, which the measurement
 * runbook waits for, then finishes. The shared function refuses unless the device is offline and the
 * owner is `LOCAL_OWNER`, so nothing is enqueued. The log line carries only counts and stable codes
 * (`docs/CONTRACTS.md §17`).
 */
class BenchmarkSeedActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            val result =
                seedBenchmarkFuelHistory(
                    graph = AndroidAppGraph.require(),
                    fuelEntryCount = BENCHMARK_FUEL_ENTRY_COUNT,
                )
            Log.i(BENCHMARK_LOG_TAG, result.logLine())
            finish()
        }
    }
}

private fun BenchmarkSeedResult.logLine(): String =
    when (this) {
        is BenchmarkSeedResult.Seeded -> "seed=ok vehicles=1 fuelEntries=$fuelEntryCount"
        is BenchmarkSeedResult.Refused -> "seed=refused reason=${reason.name}"
        is BenchmarkSeedResult.Failed -> "seed=failed code=$code written=$writtenFuelEntries"
    }

private const val BENCHMARK_LOG_TAG = "CarAppBenchmark"
