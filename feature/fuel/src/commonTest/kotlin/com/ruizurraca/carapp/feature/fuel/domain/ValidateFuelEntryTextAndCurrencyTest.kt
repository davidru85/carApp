package com.ruizurraca.carapp.feature.fuel.domain

import com.ruizurraca.carapp.core.common.MinorUnits
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.SUPPORTED_CURRENCY_CODES
import com.ruizurraca.carapp.core.common.ValidationError
import com.ruizurraca.carapp.core.model.CurrencyCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ValidateFuelEntryTextAndCurrencyTest {
    private val validate = ValidateCreateFuelEntry()
    private val context = fuelEntryValidationContext()

    @Test
    fun everySupportedCurrencyUsesExactlyOneHundredMinorUnits() {
        SUPPORTED_CURRENCY_CODES.forEach { code ->
            val currency = CurrencyCode(code)
            assertEquals(100, MinorUnits.factorFor(currency), code)
            assertEquals(
                code,
                validate(createFuelEntryCommand(currency = currency), context).okValue().currency.value,
            )
        }
    }

    @Test
    fun unsupportedExplicitCurrencyReturnsInvalidUnit() {
        val result =
            validate(
                createFuelEntryCommand(currency = CurrencyCode("JPY")),
                context,
            )

        assertEquals(Outcome.Err(ValidationError.InvalidUnit("JPY")), result)
    }

    @Test
    fun lowercaseSupportedCurrencyIsStillAnUnsupportedExplicitCode() {
        val result =
            validate(
                createFuelEntryCommand(currency = CurrencyCode("eur")),
                context,
            )

        assertEquals(Outcome.Err(ValidationError.InvalidUnit("eur")), result)
    }

    @Test
    fun notesAreTrimmedAndBlankNotesBecomeNull() {
        assertEquals(
            "fuel stop",
            validate(createFuelEntryCommand(notes = "  fuel stop  "), context).okValue().notes,
        )
        assertEquals(
            null,
            validate(createFuelEntryCommand(notes = "  \n\t "), context).okValue().notes,
        )
    }

    @Test
    fun noteLengthAcceptsBothClosedBoundsAfterNormalisation() {
        val one = "x"
        val twoHundredEighty = "x".repeat(280)

        assertEquals(one, validate(createFuelEntryCommand(notes = one), context).okValue().notes)
        assertEquals(
            twoHundredEighty,
            validate(createFuelEntryCommand(notes = twoHundredEighty), context).okValue().notes,
        )
    }

    @Test
    fun noteLengthRejectsTheFirstValueAboveItsUpperBound() {
        assertEquals(
            Outcome.Err(ValidationError.InvalidLength("notes", 1, 280)),
            validate(createFuelEntryCommand(notes = "x".repeat(281)), context),
        )
    }

    @Test
    fun validatedValuesPreserveEveryCanonicalNonDerivedField() {
        val command =
            createFuelEntryCommand(
                isFullTank = false,
                hasMissedEntries = true,
                notes = " note ",
            )
        val values = validate(command, context).okValue()

        assertEquals(command.vehicleId, values.vehicleId)
        assertEquals(command.date, values.date)
        assertEquals(command.odometerKm, values.odometerKm)
        assertEquals(false, values.isFullTank)
        assertEquals(true, values.hasMissedEntries)
        assertEquals("note", values.notes)
    }

    @Test
    fun updateNormalisesNotesWithTheSameRules() {
        val values =
            ValidateUpdateFuelEntry()(
                updateFuelEntryCommand(notes = "  edited  "),
                context,
            ).okValue()

        assertEquals("edited", values.notes)
        assertIs<CurrencyCode>(values.currency)
    }
}
