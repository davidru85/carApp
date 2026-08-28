package com.ruizurraca.carapp.feature.fuel.domain

import com.ruizurraca.carapp.core.common.Confirmation
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.model.CurrencyCode
import com.ruizurraca.carapp.core.model.EntityId
import kotlin.test.assertIs
import kotlin.time.Instant

private val defaultDate = Instant.fromEpochMilliseconds(1_700_000_000_000L)

fun fuelEntryValidationContext(
    now: Instant = Instant.fromEpochMilliseconds(1_700_100_000_000L),
    earliestAllowedDate: Instant = Instant.fromEpochMilliseconds(0L),
    vehicleInitialOdometerKm: Long = 10_000L,
    previousOdometerKm: Long? = 12_000L,
): FuelEntryValidationContext =
    FuelEntryValidationContext(
        now = now,
        earliestAllowedDate = earliestAllowedDate,
        vehicleInitialOdometerKm = vehicleInitialOdometerKm,
        previousOdometerKm = previousOdometerKm,
    )

fun createFuelEntryCommand(
    vehicleId: EntityId = EntityId("11111111-1111-4111-8111-111111111111"),
    date: Instant = defaultDate,
    odometerKm: Long = 12_500L,
    money: MoneyInput = MoneyInput.LitersAndPrice(45_123L, 1_789L),
    currency: CurrencyCode = CurrencyCode("EUR"),
    isFullTank: Boolean = true,
    hasMissedEntries: Boolean = false,
    notes: String? = null,
    confirmations: Set<Confirmation> = emptySet(),
): CreateFuelEntryCommand =
    CreateFuelEntryCommand(
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

fun updateFuelEntryCommand(
    id: EntityId = EntityId("22222222-2222-4222-8222-222222222222"),
    vehicleId: EntityId = EntityId("11111111-1111-4111-8111-111111111111"),
    date: Instant = defaultDate,
    odometerKm: Long = 12_500L,
    money: MoneyInput = MoneyInput.LitersAndPrice(45_123L, 1_789L),
    currency: CurrencyCode = CurrencyCode("EUR"),
    isFullTank: Boolean = true,
    hasMissedEntries: Boolean = false,
    notes: String? = null,
    confirmations: Set<Confirmation> = emptySet(),
): UpdateFuelEntryCommand =
    UpdateFuelEntryCommand(
        id = id,
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

fun <T> Outcome<T, *>.okValue(): T = assertIs<Outcome.Ok<T>>(this).value
