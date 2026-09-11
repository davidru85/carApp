package com.ruizurraca.carapp

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val GRAPH_STATE_EXPECTATION_TIMEOUT = 5.seconds

/**
 * Use for every graph-backed flow expectation in shared tests instead of a raw `first` wait.
 * The required description and last observed value make a missing emission an actionable assertion.
 *
 * Collection stays on the caller's context, so test-scheduler work can still run. Only the timeout
 * uses a real dispatcher: a virtual-time timeout can expire before SQLite's real work gets CPU time.
 * The latest emission is stored in a volatile holder because collection and assertion can run on
 * different workers.
 * The collector is a structured child and is cancelled and joined before graph teardown can proceed.
 * This join is intentionally unbounded: abandoning the collector could close SQLite while it is
 * still in use. Extreme CPU starvation or non-cooperative cleanup can therefore delay the assertion
 * beyond runTest's timeout. The starvation regression proves ordered cleanup of a cooperatively
 * suspended collector, not an absolute deadline for arbitrary cleanup.
 */
internal suspend fun <T> Flow<T>.awaitState(
    expectation: String,
    timeout: Duration = GRAPH_STATE_EXPECTATION_TIMEOUT,
    predicate: (T) -> Boolean,
): T =
    coroutineScope {
        val lastEmission = LastEmission()
        val emission =
            async(start = CoroutineStart.UNDISPATCHED) {
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
            result.getOrThrow()
        } finally {
            emission.cancelAndJoin()
        }
    }
