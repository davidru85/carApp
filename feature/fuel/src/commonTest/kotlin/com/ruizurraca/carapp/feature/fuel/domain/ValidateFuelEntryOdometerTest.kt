package com.ruizurraca.carapp.feature.fuel.domain

import com.ruizurraca.carapp.core.common.Confirmation
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.ValidationError
import com.ruizurraca.carapp.core.common.ValidationWarning
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ValidateFuelEntryOdometerTest {
    private val validateCreate = ValidateCreateFuelEntry()

    @Test
    fun hardOdometerRangeAcceptsBothClosedBounds() {
        val lowerContext =
            fuelEntryValidationContext(
                vehicleInitialOdometerKm = 0L,
                previousOdometerKm = null,
            )
        val upperContext =
            fuelEntryValidationContext(
                vehicleInitialOdometerKm = 0L,
                previousOdometerKm = 1_999_999L,
            )

        assertIs<Outcome.Ok<ValidatedFuelEntryValues>>(
            validateCreate(createFuelEntryCommand(odometerKm = 0L), lowerContext),
        )
        assertIs<Outcome.Ok<ValidatedFuelEntryValues>>(
            validateCreate(createFuelEntryCommand(odometerKm = 2_000_000L), upperContext),
        )
    }

    @Test
    fun hardOdometerRangeRejectsValuesBeyondBothBounds() {
        listOf(-1L, 2_000_001L).forEach { odometer ->
            assertEquals(
                Outcome.Err(ValidationError.OutOfRange("odometerKm", 0L, 2_000_000L)),
                validateCreate(
                    createFuelEntryCommand(odometerKm = odometer),
                    fuelEntryValidationContext(
                        vehicleInitialOdometerKm = 0L,
                        previousOdometerKm = null,
                    ),
                ),
            )
        }
    }

    @Test
    fun firstEntryMayEqualTheVehicleInitialOdometer() {
        assertIs<Outcome.Ok<ValidatedFuelEntryValues>>(
            validateCreate(
                createFuelEntryCommand(odometerKm = 10_000L),
                fuelEntryValidationContext(
                    vehicleInitialOdometerKm = 10_000L,
                    previousOdometerKm = null,
                ),
            ),
        )
    }

    @Test
    fun valueBelowVehicleInitialOdometerReturnsAnIdempotentWarning() {
        val command = createFuelEntryCommand(odometerKm = 9_999L)
        val context =
            fuelEntryValidationContext(
                vehicleInitialOdometerKm = 10_000L,
                previousOdometerKm = null,
            )
        val expected = Outcome.Err(ValidationWarning.OdometerInconsistent(10_000L, 9_999L))

        assertEquals(expected, validateCreate(command, context))
        assertEquals(expected, validateCreate(command, context))
        assertEquals(9_999L, command.odometerKm)
        assertEquals(10_000L, context.vehicleInitialOdometerKm)
    }

    @Test
    fun previousEntryRequiresAStrictlyGreaterOdometer() {
        val context =
            fuelEntryValidationContext(
                vehicleInitialOdometerKm = 10_000L,
                previousOdometerKm = 12_000L,
            )

        listOf(11_999L, 12_000L).forEach { entered ->
            assertEquals(
                Outcome.Err(ValidationWarning.OdometerInconsistent(12_000L, entered)),
                validateCreate(createFuelEntryCommand(odometerKm = entered), context),
            )
        }
        assertIs<Outcome.Ok<ValidatedFuelEntryValues>>(
            validateCreate(createFuelEntryCommand(odometerKm = 12_001L), context),
        )
    }

    @Test
    fun initialOdometerIsTheReferenceWhenBothConsistencyChecksFail() {
        val context =
            fuelEntryValidationContext(
                vehicleInitialOdometerKm = 10_000L,
                previousOdometerKm = 9_000L,
            )

        assertEquals(
            Outcome.Err(ValidationWarning.OdometerInconsistent(10_000L, 8_999L)),
            validateCreate(createFuelEntryCommand(odometerKm = 8_999L), context),
        )
    }

    @Test
    fun explicitOdometerConfirmationAllowsTheIdenticalCommand() {
        val context = fuelEntryValidationContext(previousOdometerKm = 12_000L)
        val unconfirmed = createFuelEntryCommand(odometerKm = 12_000L)
        val confirmed =
            unconfirmed.copy(confirmations = setOf(Confirmation.OdometerInconsistent))

        assertIs<Outcome.Err<ValidationWarning.OdometerInconsistent>>(
            validateCreate(unconfirmed, context),
        )
        assertEquals(12_000L, validateCreate(confirmed, context).okValue().odometerKm)
    }

    @Test
    fun unrelatedConfirmationDoesNotSuppressTheWarning() {
        val result =
            validateCreate(
                createFuelEntryCommand(
                    odometerKm = 12_000L,
                    confirmations = setOf(Confirmation.DeleteAccount),
                ),
                fuelEntryValidationContext(previousOdometerKm = 12_000L),
            )

        assertIs<Outcome.Err<ValidationWarning.OdometerInconsistent>>(result)
    }

    @Test
    fun confirmationNeverSuppressesAHardOdometerRangeError() {
        assertEquals(
            Outcome.Err(ValidationError.OutOfRange("odometerKm", 0L, 2_000_000L)),
            validateCreate(
                createFuelEntryCommand(
                    odometerKm = -1L,
                    confirmations = setOf(Confirmation.OdometerInconsistent),
                ),
                fuelEntryValidationContext(vehicleInitialOdometerKm = 0L, previousOdometerKm = null),
            ),
        )
    }

    @Test
    fun updateUsesTheSameWarningAndConfirmationProtocol() {
        val validateUpdate = ValidateUpdateFuelEntry()
        val context = fuelEntryValidationContext(previousOdometerKm = 12_000L)
        val unconfirmed = updateFuelEntryCommand(odometerKm = 12_000L)
        val confirmed =
            unconfirmed.copy(confirmations = setOf(Confirmation.OdometerInconsistent))

        assertEquals(
            Outcome.Err(ValidationWarning.OdometerInconsistent(12_000L, 12_000L)),
            validateUpdate(unconfirmed, context),
        )
        assertEquals(12_000L, validateUpdate(confirmed, context).okValue().odometerKm)
    }
}
