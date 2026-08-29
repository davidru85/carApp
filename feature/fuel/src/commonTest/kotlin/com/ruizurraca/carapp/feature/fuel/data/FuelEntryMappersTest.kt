package com.ruizurraca.carapp.feature.fuel.data

import com.ruizurraca.carapp.core.database.FuelEntryDatabaseRow
import com.ruizurraca.carapp.core.model.CurrencyCode
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.OwnerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class FuelEntryMappersTest {
    @Test
    fun databaseRowRoundTripsThroughTheLocalModel() {
        val row = databaseRow()

        assertEquals(row, row.toLocalFuelEntry().toDatabaseFuelEntry())
    }

    @Test
    fun localRowMapsEveryDomainField() {
        val local = databaseRow().toLocalFuelEntry()
        val domain = local.toDomainFuelEntry()

        assertEquals(EntityId(FIRST_ENTRY_ID), domain.id)
        assertEquals(OwnerId("owner-a"), domain.ownerId)
        assertEquals(EntityId(VEHICLE_ID), domain.vehicleId)
        assertEquals(Instant.fromEpochMilliseconds(2_000L), domain.date)
        assertEquals(123L, domain.odometerKm)
        assertEquals(45_123L, domain.litersScaled)
        assertEquals(1_789L, domain.pricePerLiterScaled)
        assertEquals(8_073L, domain.totalCostMinor)
        assertEquals(CurrencyCode("EUR"), domain.currency)
        assertEquals(true, domain.isFullTank)
        assertEquals(true, domain.hasMissedEntries)
        assertEquals(true, domain.odometerInconsistent)
        assertEquals("note", domain.notes)
        assertEquals(Instant.fromEpochMilliseconds(1_000L), domain.createdAt)
        assertEquals(Instant.fromEpochMilliseconds(3_000L), domain.updatedAt)
        assertEquals(Instant.fromEpochMilliseconds(4_000L), domain.deletedAt)
    }

    private fun databaseRow(): FuelEntryDatabaseRow =
        FuelEntryDatabaseRow(
            id = FIRST_ENTRY_ID,
            ownerId = "owner-a",
            vehicleId = VEHICLE_ID,
            date = 2_000L,
            odometerKm = 123L,
            litersScaled = 45_123L,
            pricePerLiterScaled = 1_789L,
            totalCostMinor = 8_073L,
            currency = "EUR",
            isFullTank = true,
            hasMissedEntries = true,
            odometerInconsistent = true,
            notes = "note",
            createdAt = 1_000L,
            updatedAt = 3_000L,
            serverUpdatedAt = 3_500L,
            deletedAt = 4_000L,
            syncState = "PENDING",
            localRevision = 2L,
            localMutationSeq = 7L,
            schemaVersion = 1L,
        )
}
