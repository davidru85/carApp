package com.ruizurraca.carapp

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class FlowExpectationTest {
    @Test
    fun starvedEmissionFailsWithTheExpectationAndLastValueBeforeTheOuterDeadline() =
        runTest {
            var collecting = false
            val source =
                flow {
                    collecting = true
                    try {
                        emit("loading")
                        awaitCancellation()
                    } finally {
                        collecting = false
                    }
                }

            val failure =
                withContext(Dispatchers.Default) {
                    withTimeoutOrNull(2.seconds) {
                        assertFailsWith<AssertionError> {
                            source.awaitState("vehicle list loaded", timeout = 20.milliseconds) { it == "loaded" }
                        }
                    }
                }

            assertNotNull(failure, "the expectation must fail before the independent outer deadline")
            assertTrue(failure.message.orEmpty().contains("vehicle list loaded"))
            assertTrue(failure.message.orEmpty().contains("loading"))
            assertFalse(collecting, "the timed-out collector must finish before returning")
        }

    @Test
    fun aFlowThatNeverEmitsReportsThatNoValueWasObserved() =
        runTest {
            val failure =
                withContext(Dispatchers.Default) {
                    withTimeoutOrNull(2.seconds) {
                        assertFailsWith<AssertionError> {
                            flow<Int> { awaitCancellation() }
                                .awaitState("first value", timeout = 20.milliseconds) { true }
                        }
                    }
                }

            assertNotNull(failure)
            assertTrue(failure.message.orEmpty().contains("no emissions"))
        }

    @Test
    fun diagnosticCapturesAnEmissionProducedOffTheCallerThread() =
        runTest {
            val source =
                flow {
                    emit("worker emission")
                    awaitCancellation()
                }.flowOn(Dispatchers.Default)

            val failure =
                withContext(Dispatchers.Default) {
                    withTimeoutOrNull(2.seconds) {
                        assertFailsWith<AssertionError> {
                            source.awaitState("worker value", timeout = 1.seconds) { it == "caller value" }
                        }
                    }
                }

            assertNotNull(failure)
            assertTrue(failure.message.orEmpty().contains("worker emission"))
        }

    @Test
    fun returnsTheFirstMatchingEmission() =
        runTest {
            assertEquals(2, flowOf(1, 2, 3).awaitState("second value") { it >= 2 })
        }

    @Test
    fun aMatchingNullValueIsNotMistakenForTimeout() =
        runTest {
            assertNull(flowOf<String?>("loading", null).awaitState("null value") { it == null })
        }

    @Test
    fun collectionKeepsTheCallerTestScheduler() =
        runTest {
            val source =
                flow {
                    delay(1.days)
                    emit("ready")
                }

            assertEquals("ready", source.awaitState("virtual-time emission") { it == "ready" })
        }

    @Test
    fun cancellationIsNotConvertedToAnAssertionAndStopsCollection() =
        runTest {
            var stopped = false
            var cancelled = false
            val job =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    try {
                        flow<Int> {
                            try {
                                awaitCancellation()
                            } finally {
                                stopped = true
                            }
                        }.awaitState("cancelled wait") { true }
                    } catch (failure: CancellationException) {
                        cancelled = true
                        throw failure
                    }
                }

            job.cancelAndJoin()

            assertTrue(cancelled)
            assertTrue(stopped)
        }

    @Test
    fun upstreamFailuresKeepTheirOriginalCause() =
        runTest {
            val cause = IllegalStateException("broken source")
            val failure =
                assertFailsWith<IllegalStateException> {
                    flow<Int> { throw cause }.awaitState("value from source") { true }
                }

            // JVM coroutine stack recovery can copy the exception and retain the original as its cause.
            assertSame(cause, generateSequence<Throwable>(failure) { it.cause }.last())
        }

    @Test
    fun independentUpstreamCancellationBecomesAValueDiagnostic() =
        runTest {
            val cause = CancellationException("source stopped")

            val failure =
                assertFailsWith<AssertionError> {
                    flow<Int> { throw cause }.awaitState("source value") { true }
                }

            assertTrue(currentCoroutineContext().isActive, "an upstream cancellation must not cancel the caller")
            assertTrue(failure.message.orEmpty().contains("source value"))
            assertSame(cause, generateSequence<Throwable>(failure) { it.cause }.last())
        }
}
