package com.ruizurraca.carapp.feature.fuel.domain

import com.ruizurraca.carapp.core.model.CurrencyCode
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.FuelEntry
import com.ruizurraca.carapp.core.model.OwnerId
import kotlin.time.Instant

internal val primaryVehicleId = EntityId("11111111-1111-4111-8111-111111111111")
internal val otherVehicleId = EntityId("22222222-2222-4222-8222-222222222222")

internal fun consumptionEntry(
    idNumber: Int,
    vehicleId: EntityId = primaryVehicleId,
    dateMillis: Long = idNumber.toLong() * 1_000L,
    odometerKm: Long,
    litersScaled: Long = 40_000L,
    isFullTank: Boolean = true,
    hasMissedEntries: Boolean = false,
    odometerInconsistent: Boolean = false,
    deletedAt: Instant? = null,
): FuelEntry =
    FuelEntry(
        id = consumptionEntryId(idNumber),
        ownerId = OwnerId("owner-a"),
        vehicleId = vehicleId,
        date = Instant.fromEpochMilliseconds(dateMillis),
        odometerKm = odometerKm,
        litersScaled = litersScaled,
        pricePerLiterScaled = 1_500L,
        totalCostMinor = 6_000L,
        currency = CurrencyCode("EUR"),
        isFullTank = isFullTank,
        hasMissedEntries = hasMissedEntries,
        odometerInconsistent = odometerInconsistent,
        notes = null,
        createdAt = Instant.fromEpochMilliseconds(dateMillis + idNumber),
        updatedAt = Instant.fromEpochMilliseconds(dateMillis + idNumber),
        deletedAt = deletedAt,
    )

internal fun consumptionEntryId(idNumber: Int): EntityId =
    EntityId("00000000-0000-4000-8000-${idNumber.toString().padStart(12, '0')}")
