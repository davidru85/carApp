package com.ruizurraca.carapp.feature.fuel.data

import com.ruizurraca.carapp.core.database.FuelEntryDatabaseRow
import com.ruizurraca.carapp.core.model.CurrencyCode
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.FuelEntry
import com.ruizurraca.carapp.core.model.OwnerId
import kotlin.time.Instant

internal data class LocalFuelEntry(
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
    val serverUpdatedAt: Instant?,
    val deletedAt: Instant?,
    val syncState: String,
    val localRevision: Long,
    val localMutationSeq: Long,
    val schemaVersion: Long,
)

internal fun FuelEntryDatabaseRow.toLocalFuelEntry(): LocalFuelEntry = redLocalFuelEntry()

internal fun LocalFuelEntry.toDatabaseFuelEntry(): FuelEntryDatabaseRow =
    FuelEntryDatabaseRow(
        id = id.value,
        ownerId = ownerId.value,
        vehicleId = vehicleId.value,
        date = date.toEpochMilliseconds(),
        odometerKm = odometerKm,
        litersScaled = litersScaled,
        pricePerLiterScaled = pricePerLiterScaled,
        totalCostMinor = totalCostMinor,
        currency = currency.value,
        isFullTank = isFullTank,
        hasMissedEntries = hasMissedEntries,
        odometerInconsistent = odometerInconsistent,
        notes = notes,
        createdAt = createdAt.toEpochMilliseconds(),
        updatedAt = updatedAt.toEpochMilliseconds(),
        serverUpdatedAt = serverUpdatedAt?.toEpochMilliseconds(),
        deletedAt = deletedAt?.toEpochMilliseconds(),
        syncState = syncState,
        localRevision = localRevision,
        localMutationSeq = localMutationSeq,
        schemaVersion = schemaVersion,
    )

internal fun LocalFuelEntry.toDomainFuelEntry(): FuelEntry = redDomainFuelEntry()

private fun redLocalFuelEntry(): LocalFuelEntry =
    LocalFuelEntry(
        id = EntityId(""),
        ownerId = OwnerId(""),
        vehicleId = EntityId(""),
        date = Instant.fromEpochMilliseconds(0L),
        odometerKm = 0L,
        litersScaled = 0L,
        pricePerLiterScaled = 0L,
        totalCostMinor = 0L,
        currency = CurrencyCode("EUR"),
        isFullTank = false,
        hasMissedEntries = false,
        odometerInconsistent = false,
        notes = null,
        createdAt = Instant.fromEpochMilliseconds(0L),
        updatedAt = Instant.fromEpochMilliseconds(0L),
        serverUpdatedAt = null,
        deletedAt = null,
        syncState = "PENDING",
        localRevision = 0L,
        localMutationSeq = 0L,
        schemaVersion = 0L,
    )

private fun redDomainFuelEntry(): FuelEntry =
    FuelEntry(
        id = EntityId(""),
        ownerId = OwnerId(""),
        vehicleId = EntityId(""),
        date = Instant.fromEpochMilliseconds(0L),
        odometerKm = 0L,
        litersScaled = 0L,
        pricePerLiterScaled = 0L,
        totalCostMinor = 0L,
        currency = CurrencyCode("EUR"),
        isFullTank = false,
        hasMissedEntries = false,
        odometerInconsistent = false,
        notes = null,
        createdAt = Instant.fromEpochMilliseconds(0L),
        updatedAt = Instant.fromEpochMilliseconds(0L),
        deletedAt = null,
    )
