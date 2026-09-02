package com.ruizurraca.carapp.feature.vehicle.data

import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.ValidationError
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.LOCAL_OWNER
import com.ruizurraca.carapp.core.model.OwnerId
import com.ruizurraca.carapp.core.sync.EntityType
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VehicleRepositoryDeleteTest {
    @Test
    fun missingDeleteReturnsEntityNotFound() =
        runTest {
            withVehicleRepositoryTestScope {
                val result = repository.deleteVehicle(EntityId(VEHICLE_ID))

                assertEquals(ValidationError.EntityNotFound, assertIs<Outcome.Err<*>>(result).error)
            }
        }

    @Test
    fun deleteTombstonesVehicleAndFuelEntriesWithFreshSharedSequences() =
        runTest {
            withVehicleRepositoryTestScope(OwnerId("owner-a")) {
                seedVehicle()
                seedFuelEntry(FIRST_FUEL_ENTRY_ID, date = 1_100, odometerKm = 20)
                seedFuelEntry(SECOND_FUEL_ENTRY_ID, date = 1_200, odometerKm = 30)

                val result = repository.deleteVehicle(EntityId(VEHICLE_ID))

                assertIs<Outcome.Ok<Unit>>(result)
                val first = requireNotNull(fuelEntry(FIRST_FUEL_ENTRY_ID))
                val second = requireNotNull(fuelEntry(SECOND_FUEL_ENTRY_ID))
                val deletedVehicle = requireNotNull(vehicle())
                assertEquals(
                    listOf(3L, 4L, 5L),
                    listOf(first.localMutationSeq, second.localMutationSeq, deletedVehicle.localMutationSeq),
                )
                listOf(first, second).forEach { row ->
                    assertEquals(1, row.deleted)
                    assertEquals(2_000, row.deletedAt)
                    assertEquals(2_000, row.updatedAt)
                    assertEquals("PENDING", row.syncState)
                    assertEquals(2, row.localRevision)
                }
                assertEquals(1, deletedVehicle.deleted)
                assertEquals(2_000, deletedVehicle.deletedAt)
                assertEquals(2_000, deletedVehicle.updatedAt)
                assertEquals("PENDING", deletedVehicle.syncState)
                assertEquals(2, deletedVehicle.localRevision)
            }
        }

    @Test
    fun localOwnerDeleteCreatesNoOutboxRows() =
        runTest {
            withVehicleRepositoryTestScope(LOCAL_OWNER) {
                seedVehicle()
                seedFuelEntry(FIRST_FUEL_ENTRY_ID, date = 1_100, odometerKm = 20)

                assertIs<Outcome.Ok<Unit>>(repository.deleteVehicle(EntityId(VEHICLE_ID)))

                assertNull(outbox(EntityType.FUEL_ENTRY.name, FIRST_FUEL_ENTRY_ID))
                assertNull(outbox(EntityType.VEHICLE.name, VEHICLE_ID))
            }
        }

    @Test
    fun permanentOwnerDeleteEnqueuesFuelTombstonesBeforeTheVehicleTombstone() =
        runTest {
            withVehicleRepositoryTestScope(OwnerId("owner-a")) {
                seedVehicle()
                seedFuelEntry(FIRST_FUEL_ENTRY_ID, date = 1_100, odometerKm = 20)
                seedFuelEntry(SECOND_FUEL_ENTRY_ID, date = 1_200, odometerKm = 30)

                assertIs<Outcome.Ok<Unit>>(repository.deleteVehicle(EntityId(VEHICLE_ID)))

                val first = requireNotNull(outbox(EntityType.FUEL_ENTRY.name, FIRST_FUEL_ENTRY_ID))
                val second = requireNotNull(outbox(EntityType.FUEL_ENTRY.name, SECOND_FUEL_ENTRY_ID))
                val vehicle = requireNotNull(outbox(EntityType.VEHICLE.name, VEHICLE_ID))
                assertTrue(first.seq < vehicle.seq)
                assertTrue(second.seq < vehicle.seq)
                listOf(first, second).forEach { queued ->
                    val payload = Json.parseToJsonElement(queued.payload).jsonObject
                    assertEquals("FUEL_ENTRY", payload.getValue("entityType").jsonPrimitive.content)
                    assertEquals("true", payload.getValue("deleted").jsonPrimitive.content)
                    assertEquals("2000", payload.getValue("deletedAt").jsonPrimitive.content)
                }
                val vehiclePayload = Json.parseToJsonElement(vehicle.payload).jsonObject
                assertEquals("VEHICLE", vehiclePayload.getValue("entityType").jsonPrimitive.content)
                assertEquals("true", vehiclePayload.getValue("deleted").jsonPrimitive.content)
                assertEquals("2000", vehiclePayload.getValue("deletedAt").jsonPrimitive.content)
            }
        }

    @Test
    fun deletingAnExistingTombstoneIsIdempotent() =
        runTest {
            withVehicleRepositoryTestScope(OwnerId("owner-a")) {
                seedVehicle(deletedAt = 1_500)
                val before = requireNotNull(vehicle())

                val result = repository.deleteVehicle(EntityId(VEHICLE_ID))

                assertIs<Outcome.Ok<Unit>>(result)
                assertEquals(before, vehicle())
            }
        }

    @Test
    fun cascadeDeleteCoalescesWithAnExistingFuelEntryOutboxRowKeepingEntityType() =
        runTest {
            val stalePayloads =
                listOf(
                    "{\"entityType\":\"FUEL_ENTRY\",\"stale\":true}",
                    "{\"entityType\":\"FUEL_ENTRY\",\"deleted\":false}",
                )
            for (stalePayload in stalePayloads) {
                withVehicleRepositoryTestScope(OwnerId("owner-a")) {
                    seedVehicle()
                    seedFuelEntry(FIRST_FUEL_ENTRY_ID, date = 1_100, odometerKm = 20)
                    seedFuelEntryOutbox(id = FIRST_FUEL_ENTRY_ID, payload = stalePayload)

                    assertIs<Outcome.Ok<Unit>>(repository.deleteVehicle(EntityId(VEHICLE_ID)))

                    val queued = requireNotNull(outbox(EntityType.FUEL_ENTRY.name, FIRST_FUEL_ENTRY_ID))
                    val payload = Json.parseToJsonElement(queued.payload).jsonObject
                    assertEquals(CANONICAL_FUEL_ENTRY_PAYLOAD_KEYS, payload.keys)
                    assertEquals("FUEL_ENTRY", payload.getValue("entityType").jsonPrimitive.content)
                    assertEquals("true", payload.getValue("deleted").jsonPrimitive.content)
                }
            }
        }

    @Test
    fun cascadeDeleteIsIdempotentWhenItIsTheLastWriterOfAFuelEntryOutboxRow() =
        runTest {
            withVehicleRepositoryTestScope(OwnerId("owner-a")) {
                seedVehicle()
                seedFuelEntry(FIRST_FUEL_ENTRY_ID, date = 1_100, odometerKm = 20)

                assertIs<Outcome.Ok<Unit>>(repository.deleteVehicle(EntityId(VEHICLE_ID)))

                val firstPayload = requireNotNull(outbox(EntityType.FUEL_ENTRY.name, FIRST_FUEL_ENTRY_ID)).payload

                assertIs<Outcome.Ok<Unit>>(repository.deleteVehicle(EntityId(VEHICLE_ID)))

                val secondPayload = requireNotNull(outbox(EntityType.FUEL_ENTRY.name, FIRST_FUEL_ENTRY_ID)).payload
                assertEquals(firstPayload, secondPayload)
            }
        }
}

private val CANONICAL_FUEL_ENTRY_PAYLOAD_KEYS =
    setOf(
        "entityType",
        "id",
        "ownerId",
        "vehicleId",
        "date",
        "odometerKm",
        "litersScaled",
        "pricePerLiterScaled",
        "totalCostMinor",
        "currency",
        "isFullTank",
        "hasMissedEntries",
        "odometerInconsistent",
        "notes",
        "createdAt",
        "updatedAt",
        "deleted",
        "deletedAt",
        "schemaVersion",
    )
