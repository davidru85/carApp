package com.ruizurraca.carapp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

/**
 * Waits for an effect a graph-backed fixture can only observe by polling: a call the graph makes on
 * a recording double, or a condition a `StateFlow` may conflate away (`D-204`, ADR-0205).
 *
 * Each round suspends for a real one-millisecond pulse on `Dispatchers.Default`. A bare `yield()`
 * would keep the caller on the virtual scheduler, while the real pulse lets `runTest` run the work
 * that the database driver has handed back to that scheduler in the meantime. The deadline is the
 * bounded real-time budget of [awaitCondition]; the pulse is not a deadline.
 */
internal suspend fun awaitGraphEffect(
    expectation: String,
    condition: suspend () -> Boolean,
) {
    awaitCondition(
        expectation = expectation,
        attempt = { withContext(Dispatchers.Default) { delay(GRAPH_EFFECT_POLL_INTERVAL) } },
        condition = condition,
    )
}

private val GRAPH_EFFECT_POLL_INTERVAL = 1.milliseconds
