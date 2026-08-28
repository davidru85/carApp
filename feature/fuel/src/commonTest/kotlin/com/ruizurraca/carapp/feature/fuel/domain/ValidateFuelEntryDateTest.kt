package com.ruizurraca.carapp.feature.fuel.domain

import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.ValidationError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class ValidateFuelEntryDateTest {
    private val validate = ValidateCreateFuelEntry()

    @Test
    fun unixEpochIsAcceptedWhenItIsTheResolvedLowerBound() {
        val epoch = Instant.fromEpochMilliseconds(0L)
        val context =
            fuelEntryValidationContext(
                now = Instant.fromEpochMilliseconds(100_000_000L),
                earliestAllowedDate = Instant.fromEpochMilliseconds(-1L),
            )

        assertIs<Outcome.Ok<ValidatedFuelEntryValues>>(
            validate(createFuelEntryCommand(date = epoch), context),
        )
    }

    @Test
    fun oneMillisecondBeforeUnixEpochIsRejectedAtTheLowerBound() {
        val context =
            fuelEntryValidationContext(
                now = Instant.fromEpochMilliseconds(100_000_000L),
                earliestAllowedDate = Instant.fromEpochMilliseconds(-1L),
            )

        assertEquals(
            Outcome.Err(ValidationError.OutOfRange("date", 0L, 103_600_000L)),
            validate(
                createFuelEntryCommand(date = Instant.fromEpochMilliseconds(-1L)),
                context,
            ),
        )
    }

    @Test
    fun resolvedVehicleLowerDateBoundIsClosed() {
        val lower = Instant.fromEpochMilliseconds(1_000_000L)
        val context =
            fuelEntryValidationContext(
                now = Instant.fromEpochMilliseconds(100_000_000L),
                earliestAllowedDate = lower,
            )

        assertIs<Outcome.Ok<ValidatedFuelEntryValues>>(
            validate(createFuelEntryCommand(date = lower), context),
        )
        assertEquals(
            Outcome.Err(ValidationError.OutOfRange("date", 1_000_000L, 103_600_000L)),
            validate(
                createFuelEntryCommand(date = Instant.fromEpochMilliseconds(999_999L)),
                context,
            ),
        )
    }

    @Test
    fun oneHourFutureToleranceIsClosed() {
        val now = Instant.fromEpochMilliseconds(100_000_000L)
        val context =
            fuelEntryValidationContext(
                now = now,
                earliestAllowedDate = Instant.fromEpochMilliseconds(0L),
            )

        assertIs<Outcome.Ok<ValidatedFuelEntryValues>>(
            validate(createFuelEntryCommand(date = now + 1.hours), context),
        )
        assertEquals(
            Outcome.Err(ValidationError.FutureDate),
            validate(
                createFuelEntryCommand(date = now + 1.hours + kotlin.time.Duration.parse("1ms")),
                context,
            ),
        )
    }

    @Test
    fun updateEnforcesTheSameDateBounds() {
        val lower = Instant.fromEpochMilliseconds(1_000_000L)
        val context =
            fuelEntryValidationContext(
                now = Instant.fromEpochMilliseconds(100_000_000L),
                earliestAllowedDate = lower,
            )

        assertEquals(
            Outcome.Err(ValidationError.OutOfRange("date", 1_000_000L, 103_600_000L)),
            ValidateUpdateFuelEntry()(
                updateFuelEntryCommand(date = Instant.fromEpochMilliseconds(999_999L)),
                context,
            ),
        )
    }
}
