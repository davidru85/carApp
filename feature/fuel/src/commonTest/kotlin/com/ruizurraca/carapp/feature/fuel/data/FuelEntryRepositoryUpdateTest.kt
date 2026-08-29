package com.ruizurraca.carapp.feature.fuel.data

import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.ValidationError
import com.ruizurraca.carapp.core.model.OwnerId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FuelEntryRepositoryUpdateTest {
    @Test
    fun updatePreservesIdentityAndCreationTimeWhileRefreshingPendingMetadata() =
        runTest {
            withFuelEntryRepositoryTestScope {
                seedVehicle()
                val created = assertIs<Outcome.Ok<com.ruizurraca.carapp.core.model.EntityId>>(
                    repository.createFuelEntry(createFuelEntryCommand()),
                ).value.value
                val before = assertNotNull(fuelEntry(created))
                clock.advanceBy(1_000L)

                assertIs<Outcome.Ok<Unit>>(
                    repository.updateFuelEntry(
                        updateFuelEntryCommand(id = created, odometerKm = 600L, notes = " changed "),
                    ),
                )
                val after = assertNotNull(fuelEntry(created))

                assertEquals(before.id, after.id)
                assertEquals(before.createdAt, after.createdAt)
                assertEquals(600L, after.odometerKm)
                assertEquals("changed", after.notes)
                assertEquals("PENDING", after.syncState)
                assertEquals(2L, after.localRevision)
                assertEquals(4L, after.localMutationSeq)
                assertEquals(NOW.toEpochMilliseconds() + 1_000L, after.updatedAt)
                assertNull(outbox(created))
            }
        }

    @Test
    fun updateExcludesItsTargetWhenResolvingThePreviousChronologicalEntry() =
        runTest {
            withFuelEntryRepositoryTestScope {
                seedVehicle()
                seedFuelEntry(FIRST_ENTRY_ID, date = ENTRY_DATE, odometerKm = 500L)

                val result = repository.updateFuelEntry(updateFuelEntryCommand(odometerKm = 500L))

                assertIs<Outcome.Ok<Unit>>(result)
            }
        }

    @Test
    fun updateDistinguishesMissingAndDeletedEntries() =
        runTest {
            withFuelEntryRepositoryTestScope {
                seedVehicle()
                val missing = repository.updateFuelEntry(updateFuelEntryCommand())
                seedFuelEntry(FIRST_ENTRY_ID, date = ENTRY_DATE, odometerKm = 500L, deletedAt = NOW)
                val deleted = repository.updateFuelEntry(updateFuelEntryCommand())

                assertEquals(ValidationError.EntityNotFound, assertIs<Outcome.Err<*>>(missing).error)
                assertEquals(ValidationError.EntityDeleted, assertIs<Outcome.Err<*>>(deleted).error)
            }
        }

    @Test
    fun permanentOwnerUpdateCoalescesTheLatestSnapshotWithoutReorderingOutbox() =
        runTest {
            withFuelEntryRepositoryTestScope(OwnerId("owner-a")) {
                seedVehicle()
                val id = assertIs<Outcome.Ok<com.ruizurraca.carapp.core.model.EntityId>>(
                    repository.createFuelEntry(createFuelEntryCommand()),
                ).value.value
                val initial = assertNotNull(outbox(id))

                assertIs<Outcome.Ok<Unit>>(
                    repository.updateFuelEntry(updateFuelEntryCommand(id = id, odometerKm = 700L)),
                )
                val updated = assertNotNull(outbox(id))

                assertEquals(initial.seq, updated.seq)
                assertEquals(2L, updated.localRevision)
                kotlin.test.assertTrue(updated.payload.contains("\"odometerKm\":700"))
            }
        }
}
