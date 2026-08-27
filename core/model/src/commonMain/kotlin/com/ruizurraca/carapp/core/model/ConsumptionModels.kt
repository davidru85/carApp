package com.ruizurraca.carapp.core.model

/** Canonical consumption result shapes from `docs/CONTRACTS.md §20.6`. */
enum class ConsumptionInvalidReason {
    NoPreviousFullTank,
    EndEntryNotFullTank,
    MissedEntriesInSegment,
    InconsistentOdometerInSegment,
    NonPositiveDistance,
    DuplicateOdometerInSegment,
}

sealed interface SegmentResult {
    data class Valid(
        val fromEntryId: EntityId,
        val toEntryId: EntityId,
        val litersScaled: Long,
        val distanceKm: Long,
        val consumption: ConsumptionL100Km,
    ) : SegmentResult

    data class Invalid(
        val toEntryId: EntityId,
        val reason: ConsumptionInvalidReason,
    ) : SegmentResult
}

data class ConsumptionReport(
    val segments: List<SegmentResult>,
    val validSegmentCount: Int,
    val average: ConsumptionL100Km?,
    val isReliable: Boolean,
)
