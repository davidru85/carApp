@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package com.ruizurraca.carapp.feature.vehicle.domain

import com.ruizurraca.carapp.core.common.Confirmation
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.FuelType
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
data class CreateVehicleCommand(
    val name: String,
    val initialOdometerKm: Long,
    val brand: String?,
    val model: String?,
    val fuelType: FuelType = FuelType.GASOLINE,
    val confirmations: Set<Confirmation>,
)

@HiddenFromObjC
data class UpdateVehicleCommand(
    val id: EntityId,
    val name: String,
    val initialOdometerKm: Long?,
    val brand: String?,
    val model: String?,
    val fuelType: FuelType,
    val confirmations: Set<Confirmation>,
)
