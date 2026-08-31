@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package com.ruizurraca.carapp.feature.fuel.domain

import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.Confirmation
import com.ruizurraca.carapp.core.common.MinorUnits
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.ValidationError
import com.ruizurraca.carapp.core.common.ValidationWarning
import com.ruizurraca.carapp.core.model.CurrencyCode
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.litersScaledOf
import com.ruizurraca.carapp.core.model.pricePerLiterScaledOf
import com.ruizurraca.carapp.core.model.totalCostMinorOf
import kotlin.native.HiddenFromObjC
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

private const val MIN_NOTES_LENGTH = 1
private const val MAX_NOTES_LENGTH = 280
private const val MIN_ODOMETER_KM = 0L
private const val MAX_ODOMETER_KM = 2_000_000L
private const val MIN_LITERS_SCALED = 1L
private const val MAX_LITERS_SCALED = 500_000L
private const val MIN_PRICE_PER_LITER_SCALED = 1L
private const val MAX_PRICE_PER_LITER_SCALED = 999_999L
private const val MIN_TOTAL_COST_MINOR = 1L
private const val MAX_TOTAL_COST_MINOR = 99_999_999L

private val UNIX_EPOCH = Instant.fromEpochMilliseconds(0L)

@HiddenFromObjC
data class FuelEntryValidationContext(
    val now: Instant,
    val earliestAllowedDate: Instant,
    val vehicleInitialOdometerKm: Long,
    val previousOdometerKm: Long?,
)

@HiddenFromObjC
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

private data class FuelEntryFields(
    val vehicleId: EntityId,
    val date: Instant,
    val odometerKm: Long,
    val money: MoneyInput,
    val currency: CurrencyCode,
    val isFullTank: Boolean,
    val hasMissedEntries: Boolean,
    val notes: String?,
    val confirmations: Set<Confirmation>,
)

@HiddenFromObjC
internal data class CanonicalMoneyValues(
    val litersScaled: Long,
    val pricePerLiterScaled: Long,
    val totalCostMinor: Long,
)

private fun outOfRange(
    field: String,
    min: Long,
    max: Long,
): ValidationError = ValidationError.OutOfRange(field, min, max)

private fun validateRange(
    field: String,
    value: Long,
    min: Long,
    max: Long,
): ValidationError? = if (value in min..max) null else outOfRange(field, min, max)

private inline fun canonicalMoneyOutcome(
    suppliedError: ValidationError?,
    derive: () -> CanonicalMoneyValues,
    derivedError: (CanonicalMoneyValues) -> ValidationError?,
): Outcome<CanonicalMoneyValues, ValidationError> =
    if (suppliedError != null) {
        Outcome.Err(suppliedError)
    } else {
        val values = derive()
        derivedError(values)?.let { Outcome.Err(it) } ?: Outcome.Ok(values)
    }

private fun resolveLitersAndPrice(
    input: MoneyInput.LitersAndPrice,
    minorUnitFactor: Int,
): Outcome<CanonicalMoneyValues, ValidationError> =
    canonicalMoneyOutcome(
        suppliedError =
            validateRange(
                "litersScaled",
                input.litersScaled,
                MIN_LITERS_SCALED,
                MAX_LITERS_SCALED,
            ) ?: validateRange(
                "pricePerLiterScaled",
                input.pricePerLiterScaled,
                MIN_PRICE_PER_LITER_SCALED,
                MAX_PRICE_PER_LITER_SCALED,
            ),
        derive = {
            CanonicalMoneyValues(
                input.litersScaled,
                input.pricePerLiterScaled,
                totalCostMinorOf(input.litersScaled, input.pricePerLiterScaled, minorUnitFactor),
            )
        },
        derivedError = {
            validateRange("totalCostMinor", it.totalCostMinor, MIN_TOTAL_COST_MINOR, MAX_TOTAL_COST_MINOR)
        },
    )

private fun resolveLitersAndTotal(
    input: MoneyInput.LitersAndTotal,
    minorUnitFactor: Int,
): Outcome<CanonicalMoneyValues, ValidationError> =
    canonicalMoneyOutcome(
        suppliedError =
            validateRange(
                "litersScaled",
                input.litersScaled,
                MIN_LITERS_SCALED,
                MAX_LITERS_SCALED,
            ) ?: validateRange(
                "totalCostMinor",
                input.totalCostMinor,
                MIN_TOTAL_COST_MINOR,
                MAX_TOTAL_COST_MINOR,
            ),
        derive = {
            CanonicalMoneyValues(
                input.litersScaled,
                pricePerLiterScaledOf(input.totalCostMinor, input.litersScaled, minorUnitFactor),
                input.totalCostMinor,
            )
        },
        derivedError = {
            validateRange(
                "pricePerLiterScaled",
                it.pricePerLiterScaled,
                MIN_PRICE_PER_LITER_SCALED,
                MAX_PRICE_PER_LITER_SCALED,
            )
        },
    )

private fun resolvePriceAndTotal(
    input: MoneyInput.PriceAndTotal,
    minorUnitFactor: Int,
): Outcome<CanonicalMoneyValues, ValidationError> =
    canonicalMoneyOutcome(
        suppliedError =
            validateRange(
                "pricePerLiterScaled",
                input.pricePerLiterScaled,
                MIN_PRICE_PER_LITER_SCALED,
                MAX_PRICE_PER_LITER_SCALED,
            ) ?: validateRange(
                "totalCostMinor",
                input.totalCostMinor,
                MIN_TOTAL_COST_MINOR,
                MAX_TOTAL_COST_MINOR,
            ),
        derive = {
            CanonicalMoneyValues(
                litersScaledOf(input.totalCostMinor, input.pricePerLiterScaled, minorUnitFactor),
                input.pricePerLiterScaled,
                input.totalCostMinor,
            )
        },
        derivedError = {
            validateRange("litersScaled", it.litersScaled, MIN_LITERS_SCALED, MAX_LITERS_SCALED)
        },
    )

@HiddenFromObjC
internal fun resolveMoney(
    input: MoneyInput,
    minorUnitFactor: Int,
): Outcome<CanonicalMoneyValues, ValidationError> =
    when (input) {
        is MoneyInput.LitersAndPrice -> resolveLitersAndPrice(input, minorUnitFactor)
        is MoneyInput.LitersAndTotal -> resolveLitersAndTotal(input, minorUnitFactor)
        is MoneyInput.PriceAndTotal -> resolvePriceAndTotal(input, minorUnitFactor)
    }

private fun hardValidationError(
    fields: FuelEntryFields,
    context: FuelEntryValidationContext,
    normalisedNotes: String?,
): ValidationError? {
    val maximumDate = context.now + 1.hours
    val minimumDate = maxOf(UNIX_EPOCH, context.earliestAllowedDate)
    return when {
        fields.date < minimumDate -> {
            outOfRange(
                "date",
                minimumDate.toEpochMilliseconds(),
                maximumDate.toEpochMilliseconds(),
            )
        }

        fields.date > maximumDate -> {
            ValidationError.FutureDate
        }

        fields.odometerKm !in MIN_ODOMETER_KM..MAX_ODOMETER_KM -> {
            outOfRange("odometerKm", MIN_ODOMETER_KM, MAX_ODOMETER_KM)
        }

        normalisedNotes != null && normalisedNotes.length !in MIN_NOTES_LENGTH..MAX_NOTES_LENGTH -> {
            ValidationError.InvalidLength(
                "notes",
                MIN_NOTES_LENGTH,
                MAX_NOTES_LENGTH,
            )
        }

        else -> {
            null
        }
    }
}

private fun odometerWarning(
    fields: FuelEntryFields,
    context: FuelEntryValidationContext,
): ValidationWarning.OdometerInconsistent? {
    val reference =
        when {
            fields.odometerKm < context.vehicleInitialOdometerKm -> {
                context.vehicleInitialOdometerKm
            }

            context.previousOdometerKm != null && fields.odometerKm <= context.previousOdometerKm -> {
                context.previousOdometerKm
            }

            else -> {
                null
            }
        }
    return reference?.let { ValidationWarning.OdometerInconsistent(it, fields.odometerKm) }
}

private fun validateFuelEntry(
    fields: FuelEntryFields,
    context: FuelEntryValidationContext,
): Outcome<ValidatedFuelEntryValues, AppError> {
    val minorUnitFactor = MinorUnits.factorFor(fields.currency)
    val normalisedNotes = fields.notes?.trim()?.takeIf(String::isNotEmpty)
    val hardError = hardValidationError(fields, context, normalisedNotes)
    return when {
        minorUnitFactor == null -> Outcome.Err(ValidationError.InvalidUnit(fields.currency.value))
        hardError != null -> Outcome.Err(hardError)
        else -> validateResolvedFields(fields, context, normalisedNotes, minorUnitFactor)
    }
}

private fun validateResolvedFields(
    fields: FuelEntryFields,
    context: FuelEntryValidationContext,
    normalisedNotes: String?,
    minorUnitFactor: Int,
): Outcome<ValidatedFuelEntryValues, AppError> =
    when (val money = resolveMoney(fields.money, minorUnitFactor)) {
        is Outcome.Err -> Outcome.Err(money.error)
        is Outcome.Ok -> validatedOutcome(fields, context, normalisedNotes, money.value)
    }

private fun validatedOutcome(
    fields: FuelEntryFields,
    context: FuelEntryValidationContext,
    normalisedNotes: String?,
    money: CanonicalMoneyValues,
): Outcome<ValidatedFuelEntryValues, AppError> {
    val warning = odometerWarning(fields, context)
    return if (warning != null && Confirmation.OdometerInconsistent !in fields.confirmations) {
        Outcome.Err(warning)
    } else {
        Outcome.Ok(
            ValidatedFuelEntryValues(
                vehicleId = fields.vehicleId,
                date = fields.date,
                odometerKm = fields.odometerKm,
                litersScaled = money.litersScaled,
                pricePerLiterScaled = money.pricePerLiterScaled,
                totalCostMinor = money.totalCostMinor,
                currency = fields.currency,
                isFullTank = fields.isFullTank,
                hasMissedEntries = fields.hasMissedEntries,
                notes = normalisedNotes,
            ),
        )
    }
}

private fun CreateFuelEntryCommand.toFields(): FuelEntryFields =
    FuelEntryFields(
        vehicleId = vehicleId,
        date = date,
        odometerKm = odometerKm,
        money = money,
        currency = currency,
        isFullTank = isFullTank,
        hasMissedEntries = hasMissedEntries,
        notes = notes,
        confirmations = confirmations,
    )

private fun UpdateFuelEntryCommand.toFields(): FuelEntryFields =
    FuelEntryFields(
        vehicleId = vehicleId,
        date = date,
        odometerKm = odometerKm,
        money = money,
        currency = currency,
        isFullTank = isFullTank,
        hasMissedEntries = hasMissedEntries,
        notes = notes,
        confirmations = confirmations,
    )

@HiddenFromObjC
class ValidateCreateFuelEntry {
    operator fun invoke(
        command: CreateFuelEntryCommand,
        context: FuelEntryValidationContext,
    ): Outcome<ValidatedFuelEntryValues, AppError> = validateFuelEntry(command.toFields(), context)
}

@HiddenFromObjC
class ValidateUpdateFuelEntry {
    operator fun invoke(
        command: UpdateFuelEntryCommand,
        context: FuelEntryValidationContext,
    ): Outcome<ValidatedFuelEntryValues, AppError> = validateFuelEntry(command.toFields(), context)
}
