package com.ruizurraca.carapp.feature.vehicle.domain

import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.ValidationError
import com.ruizurraca.carapp.core.model.EntityId

private const val MIN_TEXT_LENGTH = 1
private const val MAX_VEHICLE_TEXT_LENGTH = 40
private const val MIN_ODOMETER_KM = 0L
private const val MAX_ODOMETER_KM = 2_000_000L

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

fun canonicalVehicleName(input: String): String =
    buildString {
        var whitespacePending = false
        input.forEach { character ->
            if (character.isWhitespace()) {
                whitespacePending = isNotEmpty()
            } else {
                if (whitespacePending) append(' ')
                append(character)
                whitespacePending = false
            }
        }
    }

private fun hasDuplicateName(
    candidates: List<VehicleNameCandidate>,
    nameFold: String,
    excludedId: EntityId?,
): Boolean =
    candidates.any {
        (excludedId == null || it.id != excludedId) &&
            canonicalVehicleName(it.name).lowercase() == nameFold
    }

private fun isInvalidOdometerUpdate(
    value: Long?,
    hasNonDeletedFuelEntries: Boolean,
): Boolean =
    value != null &&
        (value !in MIN_ODOMETER_KM..MAX_ODOMETER_KM || hasNonDeletedFuelEntries)

class ValidateCreateVehicle {
    operator fun invoke(
        command: CreateVehicleCommand,
        context: CreateVehicleValidationContext,
    ): Outcome<CreateVehicleCommand, AppError> {
        val name = canonicalVehicleName(command.name)
        val brand = command.brand?.trim()?.takeIf(String::isNotEmpty)
        val model = command.model?.trim()?.takeIf(String::isNotEmpty)
        val normalised = command.copy(name = name, brand = brand, model = model)
        val nameFold = name.lowercase()
        val error =
            when {
                name.isEmpty() -> {
                    ValidationError.RequiredField("name")
                }

                name.length !in MIN_TEXT_LENGTH..MAX_VEHICLE_TEXT_LENGTH -> {
                    ValidationError.InvalidLength(
                        "name",
                        MIN_TEXT_LENGTH,
                        MAX_VEHICLE_TEXT_LENGTH,
                    )
                }

                brand != null && brand.length !in MIN_TEXT_LENGTH..MAX_VEHICLE_TEXT_LENGTH -> {
                    ValidationError.InvalidLength(
                        "brand",
                        MIN_TEXT_LENGTH,
                        MAX_VEHICLE_TEXT_LENGTH,
                    )
                }

                model != null && model.length !in MIN_TEXT_LENGTH..MAX_VEHICLE_TEXT_LENGTH -> {
                    ValidationError.InvalidLength(
                        "model",
                        MIN_TEXT_LENGTH,
                        MAX_VEHICLE_TEXT_LENGTH,
                    )
                }

                command.initialOdometerKm !in MIN_ODOMETER_KM..MAX_ODOMETER_KM -> {
                    ValidationError.OutOfRange(
                        "initialOdometerKm",
                        MIN_ODOMETER_KM,
                        MAX_ODOMETER_KM,
                    )
                }

                hasDuplicateName(context.activeVehicles, nameFold, excludedId = null) -> {
                    ValidationError.DuplicateName(name)
                }

                else -> {
                    null
                }
            }

        return if (error == null) {
            Outcome.Ok(normalised)
        } else {
            Outcome.Err(error)
        }
    }
}

class ValidateUpdateVehicle {
    operator fun invoke(
        command: UpdateVehicleCommand,
        context: UpdateVehicleValidationContext,
    ): Outcome<UpdateVehicleCommand, AppError> {
        val name = canonicalVehicleName(command.name)
        val brand = command.brand?.trim()?.takeIf(String::isNotEmpty)
        val model = command.model?.trim()?.takeIf(String::isNotEmpty)
        val normalised = command.copy(name = name, brand = brand, model = model)
        val nameFold = name.lowercase()
        val error =
            when {
                name.isEmpty() -> {
                    ValidationError.RequiredField("name")
                }

                name.length !in MIN_TEXT_LENGTH..MAX_VEHICLE_TEXT_LENGTH -> {
                    ValidationError.InvalidLength(
                        "name",
                        MIN_TEXT_LENGTH,
                        MAX_VEHICLE_TEXT_LENGTH,
                    )
                }

                brand != null && brand.length !in MIN_TEXT_LENGTH..MAX_VEHICLE_TEXT_LENGTH -> {
                    ValidationError.InvalidLength(
                        "brand",
                        MIN_TEXT_LENGTH,
                        MAX_VEHICLE_TEXT_LENGTH,
                    )
                }

                model != null && model.length !in MIN_TEXT_LENGTH..MAX_VEHICLE_TEXT_LENGTH -> {
                    ValidationError.InvalidLength(
                        "model",
                        MIN_TEXT_LENGTH,
                        MAX_VEHICLE_TEXT_LENGTH,
                    )
                }

                isInvalidOdometerUpdate(
                    command.initialOdometerKm,
                    context.hasNonDeletedFuelEntries,
                ) -> {
                    ValidationError.OutOfRange(
                        "initialOdometerKm",
                        MIN_ODOMETER_KM,
                        MAX_ODOMETER_KM,
                    )
                }

                hasDuplicateName(context.activeVehicles, nameFold, excludedId = command.id) -> {
                    ValidationError.DuplicateName(name)
                }

                else -> {
                    null
                }
            }

        return if (error == null) {
            Outcome.Ok(normalised)
        } else {
            Outcome.Err(error)
        }
    }
}
