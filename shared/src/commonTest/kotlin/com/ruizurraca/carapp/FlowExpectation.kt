package com.ruizurraca.carapp

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val GRAPH_STATE_EXPECTATION_TIMEOUT = 5.seconds

/**
 * Scheduling rounds a poll may consume before the expectation fails. Each round is one `yield()`,
 * so this is a bound on interleavings, not on wall time: a cooperative effect lands in a handful of
 * rounds, and an effect that never arrives fails by name instead of spinning.
 */
private const val CONDITION_MAX_ATTEMPTS = 10_000

/**
 * Polls [condition] until it holds, yielding between attempts, and fails the named expectation once
 * [maxAttempts] scheduling rounds elapse.
 *
 * Replaces a bare `while (condition) yield()`. That loop is unbounded and, because it never suspends
 * for real, it also never lets `runTest`'s timeout fire: an effect that never arrives spins a CPU
 * core at 100% until the CI step is killed with no test result and no diagnosis. Staying on the
 * caller's dispatcher is deliberate -- `yield()` is what lets the scheduler-dispatched work that
 * produces the awaited effect run, so the loop must not move to another dispatcher.
 *
 * [attempt] runs before each round's `yield()`, for the expectations that have to drive the effect
 * rather than merely observe it.
 */
internal suspend fun awaitCondition(
    expectation: String,
    maxAttempts: Int = CONDITION_MAX_ATTEMPTS,
    attempt: suspend () -> Unit = {},
    condition: suspend () -> Boolean,
) {
    repeat(maxAttempts) {
        if (condition()) return
        attempt()
        yield()
    }
    fail("Waited $maxAttempts scheduling rounds for $expectation; the expected effect never arrived")
}

/**
 * Budget for the collector to finish cancelling before the expectation gives up on it and fails.
 * Long enough that a cooperative collector always completes, short enough that a non-cooperative
 * one produces a named test result instead of silently stalling the whole test task.
 */
private val GRAPH_STATE_CLEANUP_TIMEOUT = 30.seconds

/**
 * Use for every graph-backed flow expectation in shared tests instead of a raw `first` wait.
 * The required description and last observed value make a missing emission an actionable assertion.
 *
 * Collection stays on the caller's context, so test-scheduler work can still run. Only the timeout
 * uses a real dispatcher: a virtual-time timeout can expire before SQLite's real work gets CPU time.
 * The latest emission is stored in a volatile holder because collection and assertion can run on
 * different workers.
 *
 * The collector is cancelled and awaited under [GRAPH_STATE_CLEANUP_TIMEOUT] before this returns, so
 * graph teardown cannot close SQLite underneath it. The await is bounded, and the collector is
 * deliberately not a structured child of the caller: a graph-backed source that ignores cancellation
 * would otherwise keep a structured join waiting forever, which turns a bounded assertion into a
 * silent stall of the entire test task -- the CI step times out with no test result and no
 * diagnosis. A collector that does not cooperate now fails this expectation by name instead. The
 * budget is generous precisely because abandoning the collector is the last resort, not the plan.
 */
internal suspend fun <T> Flow<T>.awaitState(
    expectation: String,
    timeout: Duration = GRAPH_STATE_EXPECTATION_TIMEOUT,
    cleanupTimeout: Duration = GRAPH_STATE_CLEANUP_TIMEOUT,
    predicate: (T) -> Boolean,
): T {
    val lastEmission = LastEmission()
    // Detached from the caller's job so the bounded cleanup below is authoritative. Caller
    // cancellation still stops collection, because the cleanup runs on every exit path.
    val collectorScope = CoroutineScope(currentCoroutineContext() + Job())
    val emission =
        collectorScope.async(start = CoroutineStart.UNDISPATCHED) {
            // Result distinguishes a matching null from timeout and preserves upstream failures.
            // A source cancellation is captured here so it cannot cancel the caller implicitly.
            try {
                Result.success(
                    first { value ->
                        lastEmission.value = value.toString()
                        predicate(value)
                    },
                )
            } catch (cancellation: CancellationException) {
                Result.failure(cancellation)
            } catch (failure: Throwable) {
                Result.failure(failure)
            }
        }
    try {
        val result =
            withContext(Dispatchers.Default) {
                withTimeoutOrNull(timeout) { emission.await() }
            }
        if (result == null) {
            fail("Timed out after $timeout waiting for $expectation. Last value: ${lastEmission.value}")
        }
        val upstreamFailure = result.exceptionOrNull()
        if (upstreamFailure is CancellationException) {
            // A caller cancellation reaches emission.await() directly and bypasses this branch.
            currentCoroutineContext().ensureActive()
            throw AssertionError(
                "Flow cancelled while waiting for $expectation. Last value: ${lastEmission.value}",
                upstreamFailure,
            )
        }
        return result.getOrThrow()
    } finally {
        val cleanup =
            withContext(NonCancellable + Dispatchers.Default) {
                withTimeoutOrNull(cleanupTimeout) { emission.cancelAndJoin() }
            }
        if (cleanup == null) {
            fail(
                "The collector for $expectation did not stop within " +
                    "$cleanupTimeout of being cancelled. A graph-backed flow is not " +
                    "cooperating with cancellation, so this expectation cannot be made safe to " +
                    "tear down. Last value: ${lastEmission.value}",
            )
        }
    }
}
