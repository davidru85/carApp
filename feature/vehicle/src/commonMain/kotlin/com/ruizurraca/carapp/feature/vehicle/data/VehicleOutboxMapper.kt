package com.ruizurraca.carapp.feature.vehicle.data

import com.ruizurraca.carapp.core.database.FuelEntryDatabaseRow
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
