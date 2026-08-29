package com.ruizurraca.carapp.feature.fuel.data

import com.ruizurraca.carapp.core.model.ConsumptionInvalidReason
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.FuelEntryListItem
import com.ruizurraca.carapp.core.model.SegmentResult

internal fun LocalFuelEntry.toFuelEntryListItem(segment: SegmentResult?): FuelEntryListItem =
    FuelEntryListItem(
        id = id,
        date = date,
        odometerKm = odometerKm,
        litersScaled = litersScaled,
        totalCostMinor = totalCostMinor,
        currency = currency,
        isFullTank = isFullTank,
        consumption = (segment as? SegmentResult.Valid)?.consumption,
        invalidReason =
            when {
                !isFullTank -> ConsumptionInvalidReason.EndEntryNotFullTank
                segment is SegmentResult.Invalid -> segment.reason
                else -> null
            },
    )

internal fun SegmentResult.toEntryId(): EntityId =
    when (this) {
        is SegmentResult.Invalid -> toEntryId
        is SegmentResult.Valid -> toEntryId
    }
