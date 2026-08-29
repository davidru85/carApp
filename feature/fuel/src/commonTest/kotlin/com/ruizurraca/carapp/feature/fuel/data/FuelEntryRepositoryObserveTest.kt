package com.ruizurraca.carapp.feature.fuel.data

import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.model.ConsumptionInvalidReason
import com.ruizurraca.carapp.core.model.ConsumptionL100Km
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.FuelEntryListItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Instant

class FuelEntryRepositoryObserveTest {
    @Test
    fun chronologicalListProjectsPartialRowsWhileTheyContributeToTheNextFullSegment() =
        runTest {
            withFuelEntryRepositoryTestScope {
                seedVehicle()
                seedFuelEntry(
                    FIRST_ENTRY_ID,
                    date = Instant.fromEpochMilliseconds(1_000L),
                    odometerKm = 0L,
                    litersScaled = 20_000L,
                )
                seedFuelEntry(
                    SECOND_ENTRY_ID,
                    date = Instant.fromEpochMilliseconds(2_000L),
                    odometerKm = 250L,
                    litersScaled = 10_000L,
                    isFullTank = false,
                )
                seedFuelEntry(
                    THIRD_ENTRY_ID,
                    date = Instant.fromEpochMilliseconds(3_000L),
                    odometerKm = 500L,
                    litersScaled = 30_000L,
                )

                val items =
                    assertIs<Outcome.Ok<List<FuelEntryListItem>>>(
                        repository.observeFuelEntries(EntityId(VEHICLE_ID), includeDeleted = false).first(),
                    ).value

                assertEquals(listOf(FIRST_ENTRY_ID, SECOND_ENTRY_ID, THIRD_ENTRY_ID), items.map { it.id.value })
                assertNull(items[1].consumption)
                assertEquals(ConsumptionInvalidReason.EndEntryNotFullTank, items[1].invalidReason)
                assertEquals(ConsumptionL100Km(800L), items[2].consumption)
                assertNull(items[2].invalidReason)
            }
        }

    @Test
    fun getFuelEntryIsOwnerScopedAndAbsenceIsOkNull() =
        runTest {
            withFuelEntryRepositoryTestScope(com.ruizurraca.carapp.core.model.OwnerId("owner-a")) {
                seedVehicle()
                seedFuelEntry(FIRST_ENTRY_ID, date = ENTRY_DATE, odometerKm = 100L)
                seedVehicle(id = SECOND_VEHICLE_ID, ownerId = com.ruizurraca.carapp.core.model.OwnerId("owner-b"))
                seedFuelEntry(
                    SECOND_ENTRY_ID,
                    ownerId = com.ruizurraca.carapp.core.model.OwnerId("owner-b"),
                    vehicleId = SECOND_VEHICLE_ID,
                    date = ENTRY_DATE,
                    odometerKm = 100L,
                )

                val own = assertIs<Outcome.Ok<com.ruizurraca.carapp.core.model.FuelEntry?>>(repository.getFuelEntry(EntityId(FIRST_ENTRY_ID)))
                val other = assertIs<Outcome.Ok<com.ruizurraca.carapp.core.model.FuelEntry?>>(repository.getFuelEntry(EntityId(SECOND_ENTRY_ID)))
                val missing = assertIs<Outcome.Ok<com.ruizurraca.carapp.core.model.FuelEntry?>>(repository.getFuelEntry(EntityId(THIRD_ENTRY_ID)))

                assertEquals(FIRST_ENTRY_ID, own.value?.id?.value)
                assertNull(other.value)
                assertNull(missing.value)
            }
        }
}
