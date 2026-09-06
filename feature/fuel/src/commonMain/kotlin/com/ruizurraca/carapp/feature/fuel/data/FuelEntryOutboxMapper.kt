package com.ruizurraca.carapp.feature.fuel.data

import com.ruizurraca.carapp.core.database.FuelEntryDatabaseRow
import com.ruizurraca.carapp.core.model.LOCAL_OWNER
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal fun LocalFuelEntry.toFuelEntryOutboxPayloadOrNull(): String? =
    if (ownerId == LOCAL_OWNER) {
        null
    } else {
        buildJsonObject {
            put("entityType", "FUEL_ENTRY")
            put("id", id.value)
            put("ownerId", ownerId.value)
            put("vehicleId", vehicleId.value)
            put("date", date.toEpochMilliseconds())
            put("odometerKm", odometerKm)
            put("litersScaled", litersScaled)
            put("pricePerLiterScaled", pricePerLiterScaled)
            put("totalCostMinor", totalCostMinor)
            put("currency", currency.value)
            put("isFullTank", isFullTank)
            put("hasMissedEntries", hasMissedEntries)
            put("odometerInconsistent", odometerInconsistent)
            put("notes", notes?.let(::JsonPrimitive) ?: JsonNull)
            put("createdAt", createdAt.toEpochMilliseconds())
            put("updatedAt", updatedAt.toEpochMilliseconds())
            put("deleted", deletedAt != null)
            put("deletedAt", deletedAt?.let { JsonPrimitive(it.toEpochMilliseconds()) } ?: JsonNull)
            put("schemaVersion", schemaVersion)
        }.toString()
    }

/**
 * The outbox snapshot local owner adoption enqueues for a fuel entry row it has already rewritten
 * to its new owner (`docs/CONTRACTS.md §11.4`). It is the ordinary payload of `§8`.
 */
fun FuelEntryDatabaseRow.toAdoptionOutboxPayload(): String =
    requireNotNull(toLocalFuelEntry().toFuelEntryOutboxPayloadOrNull()) {
        "adoption rewrites the owner before it builds a payload, so the sentinel cannot reach here"
    }
