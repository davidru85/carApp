package com.ruizurraca.carapp.feature.fuel.data

import com.ruizurraca.carapp.core.model.ConsumptionInvalidReason
import com.ruizurraca.carapp.core.model.ConsumptionReport
import com.ruizurraca.carapp.core.model.FuelEntryListItem

internal fun LocalFuelEntry.toFuelEntryListItem(report: ConsumptionReport): FuelEntryListItem =
    FuelEntryListItem(
        id = id,
        date = date,
        odometerKm = odometerKm,
        litersScaled = litersScaled,
        totalCostMinor = totalCostMinor,
        currency = currency,
        isFullTank = isFullTank,
        consumption = null,
        invalidReason = ConsumptionInvalidReason.EndEntryNotFullTank,
    )
