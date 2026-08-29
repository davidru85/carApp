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

internal fun FuelEntryDatabaseRow.toLocalFuelEntry(): LocalFuelEntry =
    LocalFuelEntry(
        id = EntityId(id),
        ownerId = OwnerId(ownerId),
        vehicleId = EntityId(vehicleId),
        date = Instant.fromEpochMilliseconds(date),
        odometerKm = odometerKm,
        litersScaled = litersScaled,
        pricePerLiterScaled = pricePerLiterScaled,
        totalCostMinor = totalCostMinor,
        currency = CurrencyCode(currency),
        isFullTank = isFullTank,
        hasMissedEntries = hasMissedEntries,
        odometerInconsistent = odometerInconsistent,
        notes = notes,
        createdAt = Instant.fromEpochMilliseconds(createdAt),
        updatedAt = Instant.fromEpochMilliseconds(updatedAt),
        serverUpdatedAt = serverUpdatedAt?.let(Instant::fromEpochMilliseconds),
        deletedAt = deletedAt?.let(Instant::fromEpochMilliseconds),
        syncState = syncState,
        localRevision = localRevision,
        localMutationSeq = localMutationSeq,
        schemaVersion = schemaVersion,
    )

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

internal fun LocalFuelEntry.toDomainFuelEntry(): FuelEntry =
    FuelEntry(
        id = id,
        ownerId = ownerId,
        vehicleId = vehicleId,
        date = date,
        odometerKm = odometerKm,
        litersScaled = litersScaled,
        pricePerLiterScaled = pricePerLiterScaled,
        totalCostMinor = totalCostMinor,
        currency = currency,
        isFullTank = isFullTank,
        hasMissedEntries = hasMissedEntries,
        odometerInconsistent = odometerInconsistent,
        notes = notes,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
    )
