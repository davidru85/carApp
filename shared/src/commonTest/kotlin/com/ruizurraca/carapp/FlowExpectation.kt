package com.ruizurraca.carapp

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal suspend fun <T> Flow<T>.awaitState(
    expectation: String,
    timeout: Duration = 5.seconds,
    predicate: (T) -> Boolean,
): T = first(predicate)
