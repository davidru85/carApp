package com.ruizurraca.carapp.feature.vehicle.domain

import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.ValidationError
import com.ruizurraca.carapp.core.model.FuelType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ValidateCreateVehicleTest {
    private val validate = ValidateCreateVehicle()
    private val emptyContext = CreateVehicleValidationContext(emptyList())

    @Test
    fun successReturnsTheNormalisedCommand() {
        val result =
            validate(
                createCommand(
                    name = "  Family\t car  ",
                    brand = "  Toyota  ",
                    model = "   ",
                    fuelType = FuelType.DIESEL,
                ),
                emptyContext,
            )

        val command = assertIs<Outcome.Ok<CreateVehicleCommand>>(result).value
        assertEquals("Family car", command.name)
        assertEquals("Toyota", command.brand)
        assertNull(command.model)
        assertEquals(FuelType.DIESEL, command.fuelType)
    }

    @Test
    fun blankCanonicalNameReturnsRequiredField() {
        val result = validate(createCommand(name = " \t\n "), emptyContext)

        assertEquals(ValidationError.RequiredField("name"), errorFrom(result))
    }

    @Test
    fun canonicalNameLongerThanFortyCharactersReturnsInvalidLength() {
        val result = validate(createCommand(name = " ${"n".repeat(41)} "), emptyContext)

        assertEquals(ValidationError.InvalidLength("name", 1, 40), errorFrom(result))
    }

    @Test
    fun bothCanonicalNameLengthEndsAreAccepted() {
        val lower = validate(createCommand(name = "n"), emptyContext)
        val upper = validate(createCommand(name = "n".repeat(40)), emptyContext)

        assertIs<Outcome.Ok<CreateVehicleCommand>>(lower)
        assertIs<Outcome.Ok<CreateVehicleCommand>>(upper)
    }

    @Test
    fun brandLongerThanFortyCharactersReturnsInvalidLength() {
        val result = validate(createCommand(brand = " ${"b".repeat(41)} "), emptyContext)

        assertEquals(ValidationError.InvalidLength("brand", 1, 40), errorFrom(result))
    }

    @Test
    fun modelLongerThanFortyCharactersReturnsInvalidLength() {
        val result = validate(createCommand(model = " ${"m".repeat(41)} "), emptyContext)

        assertEquals(ValidationError.InvalidLength("model", 1, 40), errorFrom(result))
    }

    @Test
    fun bothNullableTextLengthEndsAreAccepted() {
        val lower = validate(createCommand(brand = "b", model = "m"), emptyContext)
        val upper =
            validate(
                createCommand(brand = "b".repeat(40), model = "m".repeat(40)),
                emptyContext,
            )

        assertIs<Outcome.Ok<CreateVehicleCommand>>(lower)
        assertIs<Outcome.Ok<CreateVehicleCommand>>(upper)
    }

    @Test
    fun odometerBelowTheClosedRangeReturnsOutOfRange() {
        val result = validate(createCommand(initialOdometerKm = -1), emptyContext)

        assertEquals(
            ValidationError.OutOfRange("initialOdometerKm", 0, 2_000_000),
            errorFrom(result),
        )
    }

    @Test
    fun odometerAboveTheClosedRangeReturnsOutOfRange() {
        val result = validate(createCommand(initialOdometerKm = 2_000_001), emptyContext)

        assertEquals(
            ValidationError.OutOfRange("initialOdometerKm", 0, 2_000_000),
            errorFrom(result),
        )
    }

    @Test
    fun bothOdometerRangeEndsAreAccepted() {
        val lower = validate(createCommand(initialOdometerKm = 0), emptyContext)
        val upper = validate(createCommand(initialOdometerKm = 2_000_000), emptyContext)

        assertIs<Outcome.Ok<CreateVehicleCommand>>(lower)
        assertIs<Outcome.Ok<CreateVehicleCommand>>(upper)
    }

    @Test
    fun canonicalLowercaseNameMatchingAnActiveVehicleReturnsDuplicateName() {
        val context =
            CreateVehicleValidationContext(
                activeVehicles = listOf(candidate(name = "  FÄMILY\tCAR ")),
            )

        val result = validate(createCommand(name = " Fämily  Car "), context)

        assertEquals(ValidationError.DuplicateName("Fämily Car"), errorFrom(result))
    }

    @Test
    fun composedAndDecomposedUnicodeNamesRemainDistinct() {
        val context =
            CreateVehicleValidationContext(
                activeVehicles = listOf(candidate(name = "Caf\u00E9")),
            )

        val result = validate(createCommand(name = "Cafe\u0301"), context)

        assertIs<Outcome.Ok<CreateVehicleCommand>>(result)
    }
}
