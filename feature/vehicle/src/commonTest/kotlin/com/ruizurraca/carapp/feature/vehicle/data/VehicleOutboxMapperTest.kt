package com.ruizurraca.carapp.feature.vehicle.data

import com.ruizurraca.carapp.core.database.FuelEntryDatabaseRow
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.FuelType
import com.ruizurraca.carapp.core.model.LOCAL_OWNER
import com.ruizurraca.carapp.core.model.OwnerId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.time.Instant

class VehicleOutboxMapperTest {
    @Test
    fun localOwnerNeverProducesAnOutboxPayload() {
        assertNull(localVehicle(ownerId = LOCAL_OWNER).toVehicleOutboxPayloadOrNull())
    }

    @Test
    fun permanentOwnerVehiclePayloadIncludesEntityTypeVehicle() {
        val payload =
            assertNotNull(localVehicle(ownerId = OwnerId("owner-a")).toVehicleOutboxPayloadOrNull())
        val json = Json.parseToJsonElement(payload).jsonObject

        assertEquals("VEHICLE", json.getValue("entityType").jsonPrimitive.content)
    }

    @Test
    fun permanentOwnerVehiclePayloadIsACompleteCanonicalSnapshotWithoutLocalMetadata() {
        val payload =
            assertNotNull(localVehicle(ownerId = OwnerId("owner-a")).toVehicleOutboxPayloadOrNull())
        val json = Json.parseToJsonElement(payload).jsonObject

        assertEquals(MAPPER_VEHICLE_ID, json.getValue("id").jsonPrimitive.content)
        assertEquals("owner-a", json.getValue("ownerId").jsonPrimitive.content)
        assertEquals(false, json.getValue("deleted").jsonPrimitive.content.toBoolean())
        listOf("syncState", "localRevision", "localMutationSeq", "serverUpdatedAt", "nameFold", "currentOdometerKm")
            .forEach { assertFalse(it in json) }
    }

    @Test
    fun fuelEntryTombstonePayloadIncludesEntityTypeFuelEntry() {
        val payload = fuelEntryRow().toFuelEntryTombstonePayload(ownerId = "owner-a", timestamp = 2_000L)
        val json = Json.parseToJsonElement(payload).jsonObject

        assertEquals("FUEL_ENTRY", json.getValue("entityType").jsonPrimitive.content)
    }

    @Test
    fun fuelEntryTombstonePayloadContainsDeletedMarkerAndNoLocalMetadata() {
        val payload = fuelEntryRow().toFuelEntryTombstonePayload(ownerId = "owner-a", timestamp = 2_000L)
        val json = Json.parseToJsonElement(payload).jsonObject

        assertEquals(true, json.getValue("deleted").jsonPrimitive.content.toBoolean())
        assertEquals("2000", json.getValue("deletedAt").jsonPrimitive.content)
        listOf("syncState", "localRevision", "localMutationSeq", "serverUpdatedAt")
            .forEach { assertFalse(it in json) }
    }
}

private fun localVehicle(ownerId: OwnerId): LocalVehicle =
    LocalVehicle(
        id = EntityId(MAPPER_VEHICLE_ID),
        ownerId = ownerId,
        name = "Roadster",
        nameFold = "roadster",
        initialOdometerKm = 10L,
        currentOdometerKm = 50L,
        brand = "Acme",
        model = "One",
        fuelType = FuelType.GASOLINE,
        createdAt = Instant.fromEpochMilliseconds(1_000L),
        updatedAt = Instant.fromEpochMilliseconds(1_000L),
        serverUpdatedAt = null,
        deletedAt = null,
        syncState = "PENDING",
        localRevision = 1L,
        localMutationSeq = 2L,
        schemaVersion = 1L,
    )

private fun fuelEntryRow(): FuelEntryDatabaseRow =
    FuelEntryDatabaseRow(
        id = MAPPER_FIRST_FUEL_ENTRY_ID,
        ownerId = "owner-a",
        vehicleId = MAPPER_VEHICLE_ID,
        date = 1_100L,
        odometerKm = 20L,
        litersScaled = 50_000L,
        pricePerLiterScaled = 150_000L,
        totalCostMinor = 7_500L,
        currency = "EUR",
        isFullTank = true,
        hasMissedEntries = false,
        odometerInconsistent = false,
        notes = null,
        createdAt = 1_100L,
        updatedAt = 1_100L,
        serverUpdatedAt = null,
        deletedAt = null,
        syncState = "SYNCED",
        localRevision = 1L,
        localMutationSeq = 1L,
        schemaVersion = 1L,
    )

private const val MAPPER_VEHICLE_ID = "00000000-0000-4000-8000-000000000001"
private const val MAPPER_FIRST_FUEL_ENTRY_ID = "00000000-0000-4000-8000-000000000003"