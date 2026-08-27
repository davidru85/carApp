package com.ruizurraca.carapp.feature.vehicle.domain

import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.model.EntityId

data class VehicleNameCandidate(
    val id: EntityId,
    val name: String,
)

data class CreateVehicleValidationContext(
    val activeVehicles: List<VehicleNameCandidate>,
)

data class UpdateVehicleValidationContext(
    val activeVehicles: List<VehicleNameCandidate>,
    val hasNonDeletedFuelEntries: Boolean,
)

fun canonicalVehicleName(input: String): String = input

class ValidateCreateVehicle {
    operator fun invoke(
        command: CreateVehicleCommand,
        context: CreateVehicleValidationContext,
    ): Outcome<CreateVehicleCommand, AppError> = error("E1-02 RED: create validation is not implemented")
}

class ValidateUpdateVehicle {
    operator fun invoke(
        command: UpdateVehicleCommand,
        context: UpdateVehicleValidationContext,
    ): Outcome<UpdateVehicleCommand, AppError> = error("E1-02 RED: update validation is not implemented")
}
