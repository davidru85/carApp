package com.ruizurraca.carapp

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal suspend fun <T> Flow<T>.awaitState(
    expectation: String,
    timeout: Duration = 5.seconds,
    predicate: (T) -> Boolean,
): T =
    coroutineScope {
        var lastValue = "<no emissions>"
        val emission =
            async(start = CoroutineStart.UNDISPATCHED) {
                runCatching {
                    first { value ->
                        lastValue = value.toString()
                        predicate(value)
                    }
                }
            }
        try {
            val result =
                withContext(Dispatchers.Default) {
                    withTimeoutOrNull(timeout) { emission.await() }
                }
            if (result == null) fail("Timed out after $timeout waiting for $expectation. Last value: $lastValue")
            result.getOrThrow()
        } finally {
            emission.cancelAndJoin()
        }
    }
