package com.ruizurraca.carapp.feature.vehicle.data

import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.ValidationError
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.LOCAL_OWNER
import com.ruizurraca.carapp.core.model.OwnerId
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class VehicleRepositoryCreateTest {
    @Test
    fun createPersistsTheNormalisedCommand() =
        runTest {
            withVehicleRepositoryTestScope {
                val result =
                    repository.createVehicle(
                        createVehicleCommand(name = "  My\t Roadster  ", brand = "   ", model = "  One  "),
                    )

                val id = assertIs<Outcome.Ok<EntityId>>(result).value.value
                val row = requireNotNull(vehicle(id))
                assertEquals("My Roadster", row.name)
                assertEquals("my roadster", row.nameFold)
                assertNull(row.brand)
                assertEquals("One", row.model)
            }
        }

    @Test
    fun createStampsOwnerAndPendingMutationMetadata() =
        runTest {
            withVehicleRepositoryTestScope(OwnerId("owner-a")) {
                val id = assertIs<Outcome.Ok<EntityId>>(repository.createVehicle(createVehicleCommand())).value

                val row = requireNotNull(vehicle(id.value))
                assertEquals("owner-a", row.ownerId)
                assertEquals(2_000, row.createdAt)
                assertEquals(2_000, row.updatedAt)
                assertEquals("PENDING", row.syncState)
                assertEquals(1, row.localRevision)
                assertEquals(2, row.localMutationSeq)
            }
        }

    @Test
    fun localOwnerCreateStoresPendingWithoutOutbox() =
        runTest {
            withVehicleRepositoryTestScope(LOCAL_OWNER) {
                val id = assertIs<Outcome.Ok<EntityId>>(repository.createVehicle(createVehicleCommand())).value

                assertEquals("PENDING", requireNotNull(vehicle(id.value)).syncState)
                assertNull(outbox("VEHICLE", id.value))
            }
        }

    @Test
    fun permanentOwnerCreateEnqueuesTheFullVehicleSnapshot() =
        runTest {
            withVehicleRepositoryTestScope(OwnerId("owner-a")) {
                val id = assertIs<Outcome.Ok<EntityId>>(repository.createVehicle(createVehicleCommand())).value

                val queued = requireNotNull(outbox("VEHICLE", id.value))
                val payload = Json.parseToJsonElement(queued.payload).jsonObject
                assertEquals(
                    setOf(
                        "entityType",
                        "id",
                        "ownerId",
                        "name",
                        "initialOdometerKm",
                        "brand",
                        "model",
                        "fuelType",
                        "createdAt",
                        "updatedAt",
                        "deleted",
                        "deletedAt",
                        "schemaVersion",
                    ),
                    payload.keys,
                )
                assertEquals("VEHICLE", payload.getValue("entityType").jsonPrimitive.content)
                assertEquals("owner-a", payload.getValue("ownerId").jsonPrimitive.content)
                assertEquals("false", payload.getValue("deleted").jsonPrimitive.content)
                assertEquals(1, queued.localRevision)
            }
        }

    @Test
    fun duplicateCreateReturnsTheValidatorErrorWithoutMutation() =
        runTest {
            withVehicleRepositoryTestScope(OwnerId("owner-a")) {
                seedVehicle(name = "My Roadster")

                val result = repository.createVehicle(createVehicleCommand(name = "  MY\tROADSTER "))

                assertIs<ValidationError.DuplicateName>(assertIs<Outcome.Err<*>>(result).error)
                assertNull(vehicle(SECOND_VEHICLE_ID))
            }
        }
}
