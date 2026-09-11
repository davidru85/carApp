package com.ruizurraca.carapp

import kotlin.concurrent.Volatile

/**
 * Mutable diagnostic value shared by the expectation collector and its caller.
 *
 * The collector may execute on a different worker from the assertion, so the latest value must be
 * published across Kotlin/JVM and Kotlin/Native memory boundaries.
 */
internal class LastEmission {
    @Volatile
    var value: String = "<no emissions>"
}
