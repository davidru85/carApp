package com.ruizurraca.carapp.core.model

/**
 * The two canonical consumption formulas of `docs/CONTRACTS.md §2`, implemented literally as exact
 * integer arithmetic with the same HALF_UP convention as money.
 *
 * `docs/SPECIFICATION.md §6` R-3 states the mathematical definition in unscaled litres. These are
 * the only implementable form, because `litersScaled` is litres × 1000 while
 * `ConsumptionL100Km.scaled` is L/100 km × 100:
 *
 * ```text
 * L/100 km      = (litersScaled / 1000) / distanceKm * 100 = litersScaled / (10 * distanceKm)
 * scaled by 100 = 10 * litersScaled / distanceKm
 * ```
 */

/** `segmentConsumptionScaled = (10 * segmentLitersScaled + distanceKm / 2) / distanceKm` */
fun segmentConsumptionScaledOf(segmentLitersScaled: Long, distanceKm: Long): Long {
    require(distanceKm > 0L) { "distanceKm must be positive, was $distanceKm" }
    return (10L * segmentLitersScaled + distanceKm / 2L) / distanceKm
}

/**
 * `averageConsumptionScaled = (10 * sum(validSegmentLitersScaled) + sum(validSegmentDistanceKm) / 2)
 *                             / sum(validSegmentDistanceKm)`
 *
 * The average divides summed litres by summed distance. It is **not** the arithmetic mean of the
 * segment values, and it is **not** recomputed from the already-rounded segment results.
 */
fun averageConsumptionScaledOf(totalValidLitersScaled: Long, totalValidDistanceKm: Long): Long {
    require(totalValidDistanceKm > 0L) {
        "totalValidDistanceKm must be positive, was $totalValidDistanceKm"
    }
    return (10L * totalValidLitersScaled + totalValidDistanceKm / 2L) / totalValidDistanceKm
}
