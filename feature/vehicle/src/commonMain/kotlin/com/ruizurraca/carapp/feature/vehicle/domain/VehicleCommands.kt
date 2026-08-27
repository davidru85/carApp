package com.ruizurraca.carapp.feature.vehicle.domain

import com.ruizurraca.carapp.core.common.Confirmation
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.FuelType

private val defaultVehicleFuelType = FuelType.OTHER

data class CreateVehicleCommand(
    val name: String,
    val initialOdometerKm: Long,
    val brand: String?,
    val model: String?,
    val fuelType: FuelType = defaultVehicleFuelType,
    val confirmations: Set<Confirmation>,
)

data class UpdateVehicleCommand(
    val id: EntityId,
    val name: String,
    val initialOdometerKm: Long?,
    val brand: String?,
    val model: String?,
    val fuelType: FuelType,
    val confirmations: Set<Confirmation>,
)
