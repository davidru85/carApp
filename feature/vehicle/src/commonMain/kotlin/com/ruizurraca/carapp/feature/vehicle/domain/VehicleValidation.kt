package com.ruizurraca.carapp.feature.vehicle.domain

import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.ValidationError
import com.ruizurraca.carapp.core.model.EntityId

private const val MIN_TEXT_LENGTH = 1
private const val MAX_VEHICLE_TEXT_LENGTH = 40
private const val MIN_ODOMETER_KM = 0L
private const val MAX_ODOMETER_KM = 2_000_000L

private data class NormalisedVehicleFields(
    val name: String,
    val brand: String?,
    val model: String?,
)

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

private fun normaliseVehicleFields(
    name: String,
    brand: String?,
    model: String?,
): NormalisedVehicleFields =
    NormalisedVehicleFields(
        name = canonicalVehicleName(name),
        brand = brand?.trim()?.takeIf(String::isNotEmpty),
        model = model?.trim()?.takeIf(String::isNotEmpty),
    )

private fun validateVehicleFields(
    fields: NormalisedVehicleFields,
    invalidInitialOdometer: Boolean,
    candidates: List<VehicleNameCandidate>,
    excludedId: EntityId?,
): ValidationError? =
    when {
        fields.name.isEmpty() -> {
            ValidationError.RequiredField("name")
        }

        fields.name.length !in MIN_TEXT_LENGTH..MAX_VEHICLE_TEXT_LENGTH -> {
            ValidationError.InvalidLength(
                "name",
                MIN_TEXT_LENGTH,
                MAX_VEHICLE_TEXT_LENGTH,
            )
        }

        fields.brand != null && fields.brand.length !in MIN_TEXT_LENGTH..MAX_VEHICLE_TEXT_LENGTH -> {
            ValidationError.InvalidLength(
                "brand",
                MIN_TEXT_LENGTH,
                MAX_VEHICLE_TEXT_LENGTH,
            )
        }

        fields.model != null && fields.model.length !in MIN_TEXT_LENGTH..MAX_VEHICLE_TEXT_LENGTH -> {
            ValidationError.InvalidLength(
                "model",
                MIN_TEXT_LENGTH,
                MAX_VEHICLE_TEXT_LENGTH,
            )
        }

        invalidInitialOdometer -> {
            ValidationError.OutOfRange(
                "initialOdometerKm",
                MIN_ODOMETER_KM,
                MAX_ODOMETER_KM,
            )
        }

        hasDuplicateName(candidates, fields.name.lowercase(), excludedId) -> {
            ValidationError.DuplicateName(fields.name)
        }

        else -> {
            null
        }
    }

private fun <T> validationOutcome(
    value: T,
    error: ValidationError?,
): Outcome<T, AppError> =
    if (error == null) {
        Outcome.Ok(value)
    } else {
        Outcome.Err(error)
    }

class ValidateCreateVehicle {
    operator fun invoke(
        command: CreateVehicleCommand,
        context: CreateVehicleValidationContext,
    ): Outcome<CreateVehicleCommand, AppError> {
        val fields = normaliseVehicleFields(command.name, command.brand, command.model)
        val normalised = command.copy(name = fields.name, brand = fields.brand, model = fields.model)
        val error =
            validateVehicleFields(
                fields = fields,
                invalidInitialOdometer =
                    command.initialOdometerKm !in MIN_ODOMETER_KM..MAX_ODOMETER_KM,
                candidates = context.activeVehicles,
                excludedId = null,
            )

        return validationOutcome(normalised, error)
    }
}

class ValidateUpdateVehicle {
    operator fun invoke(
        command: UpdateVehicleCommand,
        context: UpdateVehicleValidationContext,
    ): Outcome<UpdateVehicleCommand, AppError> {
        val fields = normaliseVehicleFields(command.name, command.brand, command.model)
        val normalised = command.copy(name = fields.name, brand = fields.brand, model = fields.model)
        val error =
            validateVehicleFields(
                fields = fields,
                invalidInitialOdometer =
                    isInvalidOdometerUpdate(
                        command.initialOdometerKm,
                        context.hasNonDeletedFuelEntries,
                    ),
                candidates = context.activeVehicles,
                excludedId = command.id,
            )

        return validationOutcome(normalised, error)
    }
}
