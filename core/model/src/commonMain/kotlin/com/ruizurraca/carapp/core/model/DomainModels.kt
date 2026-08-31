package com.ruizurraca.carapp.core.model

import kotlin.time.Instant

/** Canonical product models from `docs/CONTRACTS.md §20.4`. Construction does not validate. */
enum class FuelType { GASOLINE, DIESEL, LPG, CNG, OTHER }

enum class DistanceUnit { KM, MILES }

enum class VolumeUnit { LITER, GALLON }

data class Vehicle(
    val id: EntityId,
    val ownerId: OwnerId,
    val name: String,
    val initialOdometerKm: Long,
    val currentOdometerKm: Long,
    val brand: String?,
    val model: String?,
    val fuelType: FuelType,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant?,
)

data class FuelEntry(
    val id: EntityId,
    val ownerId: OwnerId,
    val vehicleId: EntityId,
    val date: Instant,
    val odometerKm: Long,
    val litersScaled: Long,
    val pricePerLiterScaled: Long,
    val totalCostMinor: Long,
    val currency: CurrencyCode,
    val isFullTank: Boolean,
    val hasMissedEntries: Boolean,
    val odometerInconsistent: Boolean,
    val notes: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant?,
)

data class FuelEntryListItem(
    val id: EntityId,
    val date: Instant,
    val odometerKm: Long,
    val litersScaled: Long,
    val totalCostMinor: Long,
    val currency: CurrencyCode,
    val isFullTank: Boolean,
    val consumption: ConsumptionL100Km?,
    val invalidReason: ConsumptionInvalidReason?,
    val hasMissedEntries: Boolean,
    val odometerInconsistent: Boolean,
)

data class UserSettings(
    val currency: CurrencyCode,
    val distanceUnit: DistanceUnit,
    val volumeUnit: VolumeUnit,
    val analyticsEnabled: Boolean,
)
