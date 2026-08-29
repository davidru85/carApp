package com.ruizurraca.carapp.feature.fuel.data

import com.ruizurraca.carapp.core.model.CurrencyCode
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.LOCAL_OWNER
import com.ruizurraca.carapp.core.model.OwnerId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FuelEntryOutboxMapperTest {
    @Test
    fun localOwnerNeverProducesAnOutboxPayload() {
        assertNull(localEntry(ownerId = LOCAL_OWNER).toFuelEntryOutboxPayloadOrNull())
    }

    @Test
    fun permanentOwnerPayloadIsACompleteCanonicalSnapshotWithoutLocalMetadata() {
        val payload = assertNotNull(localEntry(ownerId = OwnerId("owner-a")).toFuelEntryOutboxPayloadOrNull())
        val json = Json.parseToJsonElement(payload).jsonObject

        assertEquals("FUEL_ENTRY", json.getValue("entityType").toString().trim('"'))
        assertEquals(FIRST_ENTRY_ID, json.getValue("id").toString().trim('"'))
        assertEquals("owner-a", json.getValue("ownerId").toString().trim('"'))
        assertEquals(false, json.getValue("deleted").toString().toBoolean())
        assertEquals("null", json.getValue("deletedAt").toString())
        listOf("syncState", "localRevision", "localMutationSeq", "serverUpdatedAt").forEach {
            assertFalse(it in json)
        }
    }
}

private fun localEntry(ownerId: OwnerId): LocalFuelEntry =
    LocalFuelEntry(
        id = EntityId(FIRST_ENTRY_ID),
        ownerId = ownerId,
        vehicleId = EntityId(VEHICLE_ID),
        date = ENTRY_DATE,
        odometerKm = 500L,
        litersScaled = 40_000L,
        pricePerLiterScaled = 1_500L,
        totalCostMinor = 6_000L,
        currency = CurrencyCode("EUR"),
        isFullTank = true,
        hasMissedEntries = false,
        odometerInconsistent = false,
        notes = null,
        createdAt = ENTRY_DATE,
        updatedAt = ENTRY_DATE,
        serverUpdatedAt = null,
        deletedAt = null,
        syncState = "PENDING",
        localRevision = 1L,
        localMutationSeq = 2L,
        schemaVersion = 1L,
    )
