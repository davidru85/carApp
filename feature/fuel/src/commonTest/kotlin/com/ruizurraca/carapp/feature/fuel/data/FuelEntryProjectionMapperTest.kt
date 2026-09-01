package com.ruizurraca.carapp.feature.fuel.data

import com.ruizurraca.carapp.core.model.ConsumptionInvalidReason
import com.ruizurraca.carapp.core.model.CurrencyCode
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.OwnerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class FuelEntryProjectionMapperTest {
    @Test
    fun partialEntryPreservesMissedAndInconsistentIndicators() {
        val projected =
            LocalFuelEntry(
                id = EntityId("00000000-0000-4000-8000-000000000101"),
                ownerId = OwnerId("owner-a"),
                vehicleId = EntityId("00000000-0000-4000-8000-000000000100"),
                date = Instant.fromEpochMilliseconds(2_000L),
                odometerKm = 123L,
                litersScaled = 45_123L,
                pricePerLiterScaled = 1_789L,
                totalCostMinor = 8_073L,
                currency = CurrencyCode("EUR"),
                isFullTank = false,
                hasMissedEntries = true,
                odometerInconsistent = true,
                notes = null,
                createdAt = Instant.fromEpochMilliseconds(1_000L),
                updatedAt = Instant.fromEpochMilliseconds(3_000L),
                serverUpdatedAt = null,
                deletedAt = null,
                syncState = "PENDING",
                localRevision = 1L,
                localMutationSeq = 1L,
                schemaVersion = 1L,
            ).toFuelEntryListItem(segment = null)

        assertEquals(ConsumptionInvalidReason.EndEntryNotFullTank, projected.invalidReason)
        assertTrue(projected.toString().contains("hasMissedEntries=true"))
        assertTrue(projected.toString().contains("odometerInconsistent=true"))
    }
}
