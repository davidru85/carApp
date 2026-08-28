package com.ruizurraca.carapp.feature.fuel.domain

import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.model.CurrencyCode
import com.ruizurraca.carapp.core.model.EntityId
import kotlin.time.Instant

data class FuelEntryValidationContext(
    val now: Instant,
    val earliestAllowedDate: Instant,
    val vehicleInitialOdometerKm: Long,
    val previousOdometerKm: Long?,
)

data class ValidatedFuelEntryValues(
    val vehicleId: EntityId,
    val date: Instant,
    val odometerKm: Long,
    val litersScaled: Long,
    val pricePerLiterScaled: Long,
    val totalCostMinor: Long,
    val currency: CurrencyCode,
    val isFullTank: Boolean,
    val hasMissedEntries: Boolean,
    val notes: String?,
)

class ValidateCreateFuelEntry {
    operator fun invoke(
        command: CreateFuelEntryCommand,
        context: FuelEntryValidationContext,
    ): Outcome<ValidatedFuelEntryValues, AppError> = error("E1-04 RED: create Fuel Entry validation is not implemented")
}

class ValidateUpdateFuelEntry {
    operator fun invoke(
        command: UpdateFuelEntryCommand,
        context: FuelEntryValidationContext,
    ): Outcome<ValidatedFuelEntryValues, AppError> = error("E1-04 RED: update Fuel Entry validation is not implemented")
}
