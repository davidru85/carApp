@file:OptIn(kotlin.experimental.ExperimentalObjCName::class)

package com.ruizurraca.carapp.feature.fuel.domain

import kotlin.native.ObjCName

/**
 * `E4-03` (`D-255`, ADR-0256): the only entry point of the non-shipping `ConsumptionBenchmark`
 * framework. It runs the `D-80` procedure unchanged - 1,000 entries, 5 discarded warm-ups, the median
 * of 20 measured runs - and returns that median in nanoseconds.
 */
@ObjCName(name = "runConsumptionBenchmark")
fun runConsumptionBenchmark(): Long = measureConsumptionBenchmark(DefaultCalculateConsumption()).medianNanos
