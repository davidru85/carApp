package com.ruizurraca.carapp.feature.fuel.domain

import com.ruizurraca.carapp.core.model.ConsumptionReport
import com.ruizurraca.carapp.core.model.FuelEntry

/** Pure R-3 contract from `docs/CONTRACTS.md §13`. */
fun interface CalculateConsumption {
    operator fun invoke(entries: List<FuelEntry>): ConsumptionReport
}

/** Default R-3 implementation. Behavior is introduced during the E1-05 GREEN phase. */
class DefaultCalculateConsumption : CalculateConsumption {
    override fun invoke(entries: List<FuelEntry>): ConsumptionReport =
        ConsumptionReport(
            segments = emptyList(),
            validSegmentCount = 0,
            average = null,
            isReliable = false,
        )
}

internal data class ConsumptionSegmentFacts(
    val litersScaled: Long,
    val hasDuplicateStartOdometer: Boolean,
    val hasMissedEntries: Boolean,
    val hasInconsistentOdometer: Boolean,
)

internal fun consumptionSegmentFacts(
    entries: List<FuelEntry>,
    fromEntry: FuelEntry,
    toEntry: FuelEntry,
): ConsumptionSegmentFacts =
    ConsumptionSegmentFacts(
        litersScaled = 0L,
        hasDuplicateStartOdometer = false,
        hasMissedEntries = false,
        hasInconsistentOdometer = false,
    )
