package com.ruizurraca.carapp.feature.fuel.data

import com.ruizurraca.carapp.core.common.Confirmation
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
import kotlin.test.assertTrue
import kotlin.time.Instant

class FuelEntryRepositoryCreateTest {
    @Test
    fun confirmedFirstEntryBelowInitialOdometerIsStoredInconsistent() =
        runTest {
            withFuelEntryRepositoryTestScope {
                seedVehicle(initialOdometerKm = 100_000L)

                val result =
                    repository.createFuelEntry(
                        createFuelEntryCommand(
                            odometerKm = 50_000L,
                            confirmations = setOf(Confirmation.OdometerInconsistent),
                        ),
                    )
                val id = assertIs<Outcome.Ok<EntityId>>(result).value.value

                assertEquals(1L, assertNotNull(fuelEntry(id)).odometerInconsistent)
            }
        }

    @Test
    fun confirmedFirstEntryBelowInitialOdometerEnqueuesInconsistentSnapshot() =
        runTest {
            withFuelEntryRepositoryTestScope(OwnerId("owner-a")) {
                seedVehicle(initialOdometerKm = 100_000L)

                val result =
                    repository.createFuelEntry(
                        createFuelEntryCommand(
                            odometerKm = 50_000L,
                            confirmations = setOf(Confirmation.OdometerInconsistent),
                        ),
                    )
                val id = assertIs<Outcome.Ok<EntityId>>(result).value.value

                assertTrue(assertNotNull(outbox(id)).payload.contains("\"odometerInconsistent\":true"))
            }
        }

    @Test
    fun firstEntryAtInitialOdometerIsStoredConsistent() =
        runTest {
            withFuelEntryRepositoryTestScope {
                seedVehicle(initialOdometerKm = 100_000L)

                val result = repository.createFuelEntry(createFuelEntryCommand(odometerKm = 100_000L))
                val id = assertIs<Outcome.Ok<EntityId>>(result).value.value

                assertEquals(0L, assertNotNull(fuelEntry(id)).odometerInconsistent)
            }
        }

    @Test
    fun createPersistsCanonicalPendingRowWithFreshSharedSequence() =
        runTest {
            withFuelEntryRepositoryTestScope {
                seedVehicle()

                val result = repository.createFuelEntry(createFuelEntryCommand())
                val id = assertIs<Outcome.Ok<EntityId>>(result).value.value
                val row = assertNotNull(fuelEntry(id))

                assertEquals(ownerContext.current.value, row.ownerId)
                assertEquals(VEHICLE_ID, row.vehicleId)
                assertEquals(40_000L, row.litersScaled)
                assertEquals(1_500L, row.pricePerLiterScaled)
                assertEquals(6_000L, row.totalCostMinor)
                assertEquals("note", row.notes)
                assertEquals("PENDING", row.syncState)
                assertEquals(1L, row.localRevision)
                assertEquals(3L, row.localMutationSeq)
                assertEquals(NOW.toEpochMilliseconds(), row.createdAt)
                assertEquals(NOW.toEpochMilliseconds(), row.updatedAt)
            }
        }

    @Test
    fun localOwnerCreateLeavesPendingRowWithoutOutbox() =
        runTest {
            withFuelEntryRepositoryTestScope {
                seedVehicle()

                val id =
                    assertIs<Outcome.Ok<EntityId>>(
                        repository.createFuelEntry(createFuelEntryCommand()),
                    ).value.value

                assertEquals("PENDING", fuelEntry(id)?.syncState)
                assertNull(outbox(id))
            }
        }

    @Test
    fun permanentOwnerCreateEnqueuesCanonicalSnapshot() =
        runTest {
            withFuelEntryRepositoryTestScope(OwnerId("owner-a")) {
                seedVehicle()

                val id =
                    assertIs<Outcome.Ok<EntityId>>(
                        repository.createFuelEntry(createFuelEntryCommand()),
                    ).value.value

                val payload = assertNotNull(outbox(id)).payload
                assertTrue(payload.contains("\"entityType\":\"FUEL_ENTRY\""))
                assertTrue(payload.contains("\"id\":\"$id\""))
            }
        }

    @Test
    fun createUsesUtcCalendarYearFactAndRejectsOneMillisecondBeforeIt() =
        runTest {
            withFuelEntryRepositoryTestScope {
                val createdAt = Instant.parse("2026-01-15T10:00:00Z")
                seedVehicle(createdAt = createdAt)
                val rejectedDate = Instant.parse("2006-01-15T09:59:59.999Z")

                val result = repository.createFuelEntry(createFuelEntryCommand(date = rejectedDate))

                val error = assertIs<Outcome.Err<*>>(result).error
                assertIs<ValidationError.OutOfRange>(error)
                assertNull(fuelEntry("00000000-0000-4000-8000-000000000001"))
            }
        }

    @Test
    fun createRejectsMissingTargetVehicleWithoutMutation() =
        runTest {
            withFuelEntryRepositoryTestScope {
                val result = repository.createFuelEntry(createFuelEntryCommand())

                assertEquals(ValidationError.EntityNotFound, assertIs<Outcome.Err<*>>(result).error)
                assertNull(fuelEntry("00000000-0000-4000-8000-000000000001"))
            }
        }
}
