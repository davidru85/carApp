package com.ruizurraca.carapp.feature.vehicle.data

import com.ruizurraca.carapp.core.database.FuelEntryDatabaseRow
import com.ruizurraca.carapp.core.database.VehicleDatabaseRow
import com.ruizurraca.carapp.core.model.LOCAL_OWNER
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal fun LocalVehicle.toVehicleOutboxPayloadOrNull(): String? =
    if (ownerId == LOCAL_OWNER) {
        null
    } else {
        buildJsonObject {
            put("entityType", "VEHICLE")
            put("id", id.value)
            put("ownerId", ownerId.value)
            put("name", name)
            put("initialOdometerKm", initialOdometerKm)
            put("brand", brand?.let(::JsonPrimitive) ?: JsonNull)
            put("model", model?.let(::JsonPrimitive) ?: JsonNull)
            put("fuelType", fuelType.name)
            put("createdAt", createdAt.toEpochMilliseconds())
            put("updatedAt", updatedAt.toEpochMilliseconds())
            put("deleted", deletedAt != null)
            put("deletedAt", deletedAt?.let { JsonPrimitive(it.toEpochMilliseconds()) } ?: JsonNull)
            put("schemaVersion", schemaVersion)
        }.toString()
    }

internal fun FuelEntryDatabaseRow.toFuelEntryTombstonePayload(
    ownerId: String,
    timestamp: Long,
): String =
    buildJsonObject {
        put("entityType", "FUEL_ENTRY")
        put("id", id)
        put("ownerId", ownerId)
        put("vehicleId", vehicleId)
        put("date", date)
        put("odometerKm", odometerKm)
        put("litersScaled", litersScaled)
        put("pricePerLiterScaled", pricePerLiterScaled)
        put("totalCostMinor", totalCostMinor)
        put("currency", currency)
        put("isFullTank", isFullTank)
        put("hasMissedEntries", hasMissedEntries)
        put("odometerInconsistent", odometerInconsistent)
        put("notes", notes?.let(::JsonPrimitive) ?: JsonNull)
        put("createdAt", createdAt)
        put("updatedAt", timestamp)
        put("deleted", true)
        put("deletedAt", timestamp)
        put("schemaVersion", schemaVersion)
    }.toString()

/**
 * The outbox snapshot local owner adoption enqueues for a vehicle row it has already rewritten to
 * its new owner (`docs/CONTRACTS.md §11.4`). It is the ordinary payload of `§8`, built from the
 * stored row rather than from a command, so an adopted snapshot and an edited one are identical.
 */
fun VehicleDatabaseRow.toAdoptionOutboxPayload(): String =
    requireNotNull(toLocalVehicle().toVehicleOutboxPayloadOrNull()) {
        "adoption rewrites the owner before it builds a payload, so the sentinel cannot reach here"
    }
