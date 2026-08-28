package com.ruizurraca.carapp.feature.vehicle.data

import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.ValidationError
import com.ruizurraca.carapp.core.model.OwnerId
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class VehicleRepositoryUpdateTest {
    @Test
    fun missingUpdateReturnsEntityNotFound() =
        runTest {
            withVehicleRepositoryTestScope {
                val result = repository.updateVehicle(updateVehicleCommand())

                assertEquals(ValidationError.EntityNotFound, assertIs<Outcome.Err<*>>(result).error)
            }
        }

    @Test
    fun tombstonedUpdateReturnsEntityDeleted() =
        runTest {
            withVehicleRepositoryTestScope(OwnerId("owner-a")) {
                seedVehicle(deletedAt = 1_500)

                val result = repository.updateVehicle(updateVehicleCommand())

                assertEquals(ValidationError.EntityDeleted, assertIs<Outcome.Err<*>>(result).error)
            }
        }

    @Test
    fun updatePreservesReadOnlyFieldsAndStampsFreshMutationMetadata() =
        runTest {
            withVehicleRepositoryTestScope(OwnerId("owner-a")) {
                seedVehicle(initialOdometerKm = 10)
                seedFuelEntry(FIRST_FUEL_ENTRY_ID, date = 1_100, odometerKm = 50)

                val result =
                    repository.updateVehicle(
                        updateVehicleCommand(
                            name = "  Updated\tRoadster ",
                            initialOdometerKm = null,
                            brand = " ",
                            model = " Two ",
                        ),
                    )

                assertIs<Outcome.Ok<Unit>>(result)
                val row = requireNotNull(vehicle())
                assertEquals("owner-a", row.ownerId)
                assertEquals("Updated Roadster", row.name)
                assertEquals("updated roadster", row.nameFold)
                assertEquals(10, row.initialOdometerKm)
                assertEquals(50, row.currentOdometerKm)
                assertEquals(CREATED_AT, row.createdAt)
                assertEquals(2_000, row.updatedAt)
                assertEquals(null, row.brand)
                assertEquals("Two", row.model)
                assertEquals("PENDING", row.syncState)
                assertEquals(2, row.localRevision)
                assertEquals(3, row.localMutationSeq)
            }
        }

    @Test
    fun updateCoalescesTheOutboxAtTheOriginalSequence() =
        runTest {
            withVehicleRepositoryTestScope(OwnerId("owner-a")) {
                seedVehicle(outboxPayload = "{\"stale\":true}")
                val original = requireNotNull(outbox("VEHICLE", VEHICLE_ID))

                assertIs<Outcome.Ok<Unit>>(repository.updateVehicle(updateVehicleCommand()))

                val queued = requireNotNull(outbox("VEHICLE", VEHICLE_ID))
                val payload = Json.parseToJsonElement(queued.payload).jsonObject
                assertEquals(original.seq, queued.seq)
                assertEquals(2, queued.localRevision)
                assertEquals("Roadster Updated", payload.getValue("name").jsonPrimitive.content)
                assertEquals("false", payload.getValue("deleted").jsonPrimitive.content)
            }
        }

    @Test
    fun updateLoadsTheFuelEntryFactForOdometerEditLocking() =
        runTest {
            withVehicleRepositoryTestScope(OwnerId("owner-a")) {
                seedVehicle(initialOdometerKm = 10)
                seedFuelEntry(FIRST_FUEL_ENTRY_ID, date = 1_100, odometerKm = 50)

                val result = repository.updateVehicle(updateVehicleCommand(initialOdometerKm = 20))

                assertEquals(
                    ValidationError.EditNotAllowed("initialOdometerKm"),
                    assertIs<Outcome.Err<*>>(result).error,
                )
                assertEquals(10, requireNotNull(vehicle()).initialOdometerKm)
            }
        }

    @Test
    fun updateLoadsCurrentOwnerCandidatesForDuplicateValidation() =
        runTest {
            withVehicleRepositoryTestScope(OwnerId("owner-a")) {
                seedVehicle(name = "First")
                seedVehicle(id = SECOND_VEHICLE_ID, name = "Second")

                val result = repository.updateVehicle(updateVehicleCommand(name = " SECOND "))

                assertEquals(
                    ValidationError.DuplicateName("SECOND"),
                    assertIs<Outcome.Err<*>>(result).error,
                )
                assertEquals("First", requireNotNull(vehicle()).name)
            }
        }
}
