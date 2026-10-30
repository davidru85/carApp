package com.ruizurraca.carapp.feature.fuel.domain

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** `E4-03` (`D-255`): the boundary of the `D-80` `< 100 ms` rule. */
class ConsumptionBenchmarkThresholdTest {
    @Test
    fun aMedianOfExactlyOneHundredMillisecondsMissesTheD80Target() {
        assertTrue(exceedsConsumptionThreshold(CONSUMPTION_THRESHOLD_NANOS))
    }

    @Test
    fun aMedianOneNanosecondBelowOneHundredMillisecondsMeetsTheD80Target() {
        assertFalse(exceedsConsumptionThreshold(CONSUMPTION_THRESHOLD_NANOS - 1L))
    }
}
