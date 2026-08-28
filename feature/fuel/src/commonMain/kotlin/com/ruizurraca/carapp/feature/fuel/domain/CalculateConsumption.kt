package com.ruizurraca.carapp.feature.fuel.domain

import com.ruizurraca.carapp.core.model.ConsumptionInvalidReason
import com.ruizurraca.carapp.core.model.ConsumptionL100Km
import com.ruizurraca.carapp.core.model.ConsumptionReport
import com.ruizurraca.carapp.core.model.FuelEntry
import com.ruizurraca.carapp.core.model.SegmentResult
import com.ruizurraca.carapp.core.model.averageConsumptionScaledOf
import com.ruizurraca.carapp.core.model.segmentConsumptionScaledOf

/** Pure R-3 contract from `docs/CONTRACTS.md §13`. */
fun interface CalculateConsumption {
    operator fun invoke(entries: List<FuelEntry>): ConsumptionReport
}

/** Default pure R-3 implementation. */
class DefaultCalculateConsumption : CalculateConsumption {
    override fun invoke(entries: List<FuelEntry>): ConsumptionReport {
        val calculationOrder = entries.sortedWith(CALCULATION_ORDER)
        val segments = mutableListOf<SegmentResult>()
        var previousFullTank: FuelEntry? = null
        var totalValidLitersScaled = 0L
        var totalValidDistanceKm = 0L

        calculationOrder.forEach { entry ->
            if (!entry.isFullTank) return@forEach
            val fromEntry = previousFullTank
            val result =
                if (fromEntry == null) {
                    SegmentResult.Invalid(entry.id, ConsumptionInvalidReason.NoPreviousFullTank)
                } else {
                    calculateSegment(calculationOrder, fromEntry, entry)
                }
            segments += result
            if (result is SegmentResult.Valid) {
                totalValidLitersScaled += result.litersScaled
                totalValidDistanceKm += result.distanceKm
            }
            previousFullTank = entry
        }

        val validSegmentCount = segments.count { it is SegmentResult.Valid }
        val average =
            if (validSegmentCount == 0) {
                null
            } else {
                ConsumptionL100Km(
                    averageConsumptionScaledOf(totalValidLitersScaled, totalValidDistanceKm),
                )
            }
        return ConsumptionReport(
            segments = segments,
            validSegmentCount = validSegmentCount,
            average = average,
            isReliable = validSegmentCount >= 2,
        )
    }

    private fun calculateSegment(
        entries: List<FuelEntry>,
        fromEntry: FuelEntry,
        toEntry: FuelEntry,
    ): SegmentResult {
        val distanceKm = toEntry.odometerKm - fromEntry.odometerKm
        if (distanceKm <= 0L) {
            return SegmentResult.Invalid(
                toEntryId = toEntry.id,
                reason = ConsumptionInvalidReason.NonPositiveDistance,
            )
        }
        val facts = consumptionSegmentFacts(entries, fromEntry, toEntry)
        val invalidReason =
            when {
                facts.hasDuplicateStartOdometer -> {
                    ConsumptionInvalidReason.DuplicateOdometerInSegment
                }

                facts.hasMissedEntries -> {
                    ConsumptionInvalidReason.MissedEntriesInSegment
                }

                facts.hasInconsistentOdometer -> {
                    ConsumptionInvalidReason.InconsistentOdometerInSegment
                }

                else -> {
                    null
                }
            }
        return if (invalidReason == null) {
            SegmentResult.Valid(
                fromEntryId = fromEntry.id,
                toEntryId = toEntry.id,
                litersScaled = facts.litersScaled,
                distanceKm = distanceKm,
                consumption =
                    ConsumptionL100Km(
                        segmentConsumptionScaledOf(facts.litersScaled, distanceKm),
                    ),
            )
        } else {
            SegmentResult.Invalid(toEntry.id, invalidReason)
        }
    }

    private companion object {
        val CALCULATION_ORDER =
            compareBy<FuelEntry>(FuelEntry::odometerKm)
                .thenBy(FuelEntry::date)
                .thenBy { it.id.value }
    }
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
): ConsumptionSegmentFacts {
    val members =
        entries.filter { entry ->
            entry.id != fromEntry.id &&
                when {
                    entry.odometerKm == fromEntry.odometerKm -> {
                        true
                    }

                    entry.odometerKm > fromEntry.odometerKm -> {
                        entry.odometerKm <= toEntry.odometerKm
                    }

                    else -> {
                        false
                    }
                }
        }
    return ConsumptionSegmentFacts(
        litersScaled = members.sumOf(FuelEntry::litersScaled),
        hasDuplicateStartOdometer =
            members.any { it.odometerKm == fromEntry.odometerKm },
        hasMissedEntries = members.any(FuelEntry::hasMissedEntries),
        hasInconsistentOdometer = members.any(FuelEntry::odometerInconsistent),
    )
}
