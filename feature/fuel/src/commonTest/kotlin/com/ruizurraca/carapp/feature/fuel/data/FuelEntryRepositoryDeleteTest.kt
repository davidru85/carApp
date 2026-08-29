package com.ruizurraca.carapp.feature.fuel.data

import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.ValidationError
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.OwnerId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FuelEntryRepositoryDeleteTest {
    @Test
    fun deleteCreatesPendingTombstoneWithFreshSequenceAndNoLocalOwnerOutbox() =
        runTest {
            withFuelEntryRepositoryTestScope {
                seedVehicle()
                val id = assertIs<Outcome.Ok<EntityId>>(repository.createFuelEntry(createFuelEntryCommand())).value.value
                clock.advanceBy(1_000L)

                assertIs<Outcome.Ok<Unit>>(repository.deleteFuelEntry(EntityId(id)))
                val row = assertNotNull(fuelEntry(id))

                assertEquals(1L, row.deleted)
                assertEquals(NOW.toEpochMilliseconds() + 1_000L, row.deletedAt)
                assertEquals("PENDING", row.syncState)
                assertEquals(2L, row.localRevision)
                assertEquals(3L, row.localMutationSeq)
                assertNull(outbox(id))
            }
        }

    @Test
    fun logicalDeleteRecomputesSuccessorAndVehicleCurrentOdometer() =
        runTest {
            withFuelEntryRepositoryTestScope {
                seedVehicle(initialOdometerKm = 0L)
                seedFuelEntry(FIRST_ENTRY_ID, date = ENTRY_DATE, odometerKm = 200L)
                seedFuelEntry(SECOND_ENTRY_ID, date = ENTRY_DATE + kotlin.time.Duration.parse("1s"), odometerKm = 100L)
                assertEquals(1L, fuelEntry(SECOND_ENTRY_ID)?.odometerInconsistent)

                assertIs<Outcome.Ok<Unit>>(repository.deleteFuelEntry(EntityId(FIRST_ENTRY_ID)))

                assertEquals(0L, fuelEntry(SECOND_ENTRY_ID)?.odometerInconsistent)
                val vehicle = database.databaseQueries.selectVehicleById(VEHICLE_ID).executeAsOne()
                assertEquals(100L, vehicle.currentOdometerKm)
            }
        }

    @Test
    fun deleteRejectsMissingEntry() =
        runTest {
            withFuelEntryRepositoryTestScope {
                assertEquals(
                    ValidationError.EntityNotFound,
                    assertIs<Outcome.Err<*>>(repository.deleteFuelEntry(EntityId(FIRST_ENTRY_ID))).error,
                )
            }
        }

    @Test
    fun permanentOwnerDeleteCoalescesACompleteTombstoneSnapshot() =
        runTest {
            withFuelEntryRepositoryTestScope(OwnerId("owner-a")) {
                seedVehicle()
                val id = assertIs<Outcome.Ok<EntityId>>(repository.createFuelEntry(createFuelEntryCommand())).value.value

                assertIs<Outcome.Ok<Unit>>(repository.deleteFuelEntry(EntityId(id)))
                val outbox = assertNotNull(outbox(id))

                assertEquals(2L, outbox.localRevision)
                kotlin.test.assertTrue(outbox.payload.contains("\"deleted\":true"))
                kotlin.test.assertTrue(outbox.payload.contains("\"deletedAt\":${NOW.toEpochMilliseconds()}"))
            }
        }
}
