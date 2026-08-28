package com.ruizurraca.carapp.feature.vehicle.domain

import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.ValidationError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ValidateUpdateVehicleTest {
    private val validate = ValidateUpdateVehicle()
    private val emptyContext = UpdateVehicleValidationContext(emptyList(), false)

    @Test
    fun successReturnsTheNormalisedCommand() {
        val result =
            validate(
                updateCommand(name = "  Family\t car  ", brand = "  Toyota  ", model = "   "),
                emptyContext,
            )

        val command = assertIs<Outcome.Ok<UpdateVehicleCommand>>(result).value
        assertEquals("Family car", command.name)
        assertEquals("Toyota", command.brand)
        assertNull(command.model)
    }

    @Test
    fun blankCanonicalNameReturnsRequiredField() {
        val result = validate(updateCommand(name = " \t\n "), emptyContext)

        assertEquals(ValidationError.RequiredField("name"), errorFrom(result))
    }

    @Test
    fun canonicalNameLongerThanFortyCharactersReturnsInvalidLength() {
        val result = validate(updateCommand(name = " ${"n".repeat(41)} "), emptyContext)

        assertEquals(ValidationError.InvalidLength("name", 1, 40), errorFrom(result))
    }

    @Test
    fun bothCanonicalNameLengthEndsAreAccepted() {
        val lower = validate(updateCommand(name = "n"), emptyContext)
        val upper = validate(updateCommand(name = "n".repeat(40)), emptyContext)

        assertIs<Outcome.Ok<UpdateVehicleCommand>>(lower)
        assertIs<Outcome.Ok<UpdateVehicleCommand>>(upper)
    }

    @Test
    fun brandLongerThanFortyCharactersReturnsInvalidLength() {
        val result = validate(updateCommand(brand = " ${"b".repeat(41)} "), emptyContext)

        assertEquals(ValidationError.InvalidLength("brand", 1, 40), errorFrom(result))
    }

    @Test
    fun modelLongerThanFortyCharactersReturnsInvalidLength() {
        val result = validate(updateCommand(model = " ${"m".repeat(41)} "), emptyContext)

        assertEquals(ValidationError.InvalidLength("model", 1, 40), errorFrom(result))
    }

    @Test
    fun bothNullableTextLengthEndsAreAccepted() {
        val lower = validate(updateCommand(brand = "b", model = "m"), emptyContext)
        val upper =
            validate(
                updateCommand(brand = "b".repeat(40), model = "m".repeat(40)),
                emptyContext,
            )

        assertIs<Outcome.Ok<UpdateVehicleCommand>>(lower)
        assertIs<Outcome.Ok<UpdateVehicleCommand>>(upper)
    }

    @Test
    fun odometerBelowTheClosedRangeReturnsOutOfRange() {
        val result = validate(updateCommand(initialOdometerKm = -1), emptyContext)

        assertEquals(
            ValidationError.OutOfRange("initialOdometerKm", 0, 2_000_000),
            errorFrom(result),
        )
    }

    @Test
    fun odometerOutsideTheClosedRangeReturnsOutOfRange() {
        val result = validate(updateCommand(initialOdometerKm = 2_000_001), emptyContext)

        assertEquals(
            ValidationError.OutOfRange("initialOdometerKm", 0, 2_000_000),
            errorFrom(result),
        )
    }

    @Test
    fun bothOdometerRangeEndsAreAcceptedWithoutFuelEntries() {
        val lower = validate(updateCommand(initialOdometerKm = 0), emptyContext)
        val upper = validate(updateCommand(initialOdometerKm = 2_000_000), emptyContext)

        assertIs<Outcome.Ok<UpdateVehicleCommand>>(lower)
        assertIs<Outcome.Ok<UpdateVehicleCommand>>(upper)
    }

    @Test
    fun nonNullOdometerWithExistingFuelEntriesReturnsEditNotAllowed() {
        val context = UpdateVehicleValidationContext(emptyList(), true)

        val result = validate(updateCommand(initialOdometerKm = 10_000), context)

        assertEquals("VALIDATION.EDIT_NOT_ALLOWED", errorFrom(result).code)
    }

    @Test
    fun unchangedOdometerIsAcceptedWithExistingFuelEntries() {
        val context = UpdateVehicleValidationContext(emptyList(), true)

        val result = validate(updateCommand(initialOdometerKm = null), context)

        assertIs<Outcome.Ok<UpdateVehicleCommand>>(result)
    }

    @Test
    fun canonicalLowercaseNameMatchingAnotherVehicleReturnsDuplicateName() {
        val context =
            UpdateVehicleValidationContext(
                activeVehicles = listOf(candidate(name = "  FÄMILY\tCAR ")),
                hasNonDeletedFuelEntries = false,
            )

        val result = validate(updateCommand(name = " Fämily  Car "), context)

        assertEquals(ValidationError.DuplicateName("Fämily Car"), errorFrom(result))
    }

    @Test
    fun currentVehicleIsExcludedFromTheDuplicateNameCheck() {
        val command = updateCommand(name = " Family Car ")
        val context =
            UpdateVehicleValidationContext(
                activeVehicles = listOf(VehicleNameCandidate(command.id, "family car")),
                hasNonDeletedFuelEntries = false,
            )

        val result = validate(command, context)

        assertIs<Outcome.Ok<UpdateVehicleCommand>>(result)
    }
}
