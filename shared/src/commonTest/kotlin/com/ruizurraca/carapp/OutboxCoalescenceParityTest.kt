package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.database.DatabaseMutations
import com.ruizurraca.carapp.core.database.FuelEntryDatabaseRow
import com.ruizurraca.carapp.core.database.VehicleDatabaseAccess
import com.ruizurraca.carapp.core.database.outboxPayloadByEntity
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.FuelType
import com.ruizurraca.carapp.core.model.OwnerId
import com.ruizurraca.carapp.core.sync.EntityType
import com.ruizurraca.carapp.core.testing.FakeAppClock
import com.ruizurraca.carapp.core.testing.FakeOwnerContext
import com.ruizurraca.carapp.core.testing.FakeUuidGenerator
import com.ruizurraca.carapp.core.testing.InMemoryDatabaseFactory
import com.ruizurraca.carapp.feature.vehicle.data.SqlDelightVehicleRepository
import com.ruizurraca.carapp.feature.vehicle.domain.CreateVehicleCommand
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Instant

/**
 * Cross-feature coalescence parity test: proves the direct Fuel Entry writer (via DatabaseMutations)
 * and the Vehicle cascade-delete writer (via SqlDelightVehicleRepository) produce outbox payloads
 * with the same canonical key set. Lives in `:shared` because it depends on both `:feature:vehicle`
 * and `:core:database`, respecting the "features do not depend on other features" architecture rule.
 */
class OutboxCoalescenceParityTest {
    @Test
    fun directFuelEntryWriteAndCascadeDeleteProduceTheSameCanonicalKeySet() =
        runTest {
            val fixture = CoalescenceFixture()
            try {
                val vehicleId = fixture.createVehicle()
                fixture.createDirectFuelEntry(vehicleId)

                val directPayload = fixture.fuelEntryOutboxPayload()
                val directKeys = Json.parseToJsonElement(directPayload).jsonObject.keys
                assertEquals(CANONICAL_FUEL_ENTRY_PAYLOAD_KEYS, directKeys)

                fixture.deleteVehicle(vehicleId)

                val cascadePayload = fixture.fuelEntryOutboxPayload()
                val cascadeJson = Json.parseToJsonElement(cascadePayload).jsonObject

                assertEquals(directKeys, cascadeJson.keys)
                assertEquals(CANONICAL_FUEL_ENTRY_PAYLOAD_KEYS, cascadeJson.keys)
            } finally {
                fixture.close()
            }
        }
}

private class CoalescenceFixture {
    private val databaseFactory = InMemoryDatabaseFactory()
    private val databaseHandle = databaseFactory.create()
    private val database = databaseHandle.database
    private val mutations = DatabaseMutations(database)
    private val owner = OwnerId("owner-a")
    private val repository =
        SqlDelightVehicleRepository(
            VehicleDatabaseAccess(database),
            FakeOwnerContext(owner),
            FakeAppClock(Instant.fromEpochMilliseconds(2_000)),
            FakeUuidGenerator(),
        )

    suspend fun createVehicle(): String =
        repository
            .createVehicle(
                CreateVehicleCommand(
                    name = "Roadster",
                    initialOdometerKm = 10,
                    brand = "Acme",
                    model = "One",
                    fuelType = FuelType.GASOLINE,
                    confirmations = emptySet(),
                ),
            ).let { it as Outcome.Ok }
            .value.value

    suspend fun createDirectFuelEntry(vehicleId: String) {
        mutations.insertLocalFuelEntry(
            entry = fuelEntryRow(vehicleId),
            outboxPayload = { row -> buildDirectFuelEntryPayload(row.id, owner.value, row.vehicleId) },
        )
    }

    suspend fun deleteVehicle(vehicleId: String) {
        repository.deleteVehicle(EntityId(vehicleId))
    }

    suspend fun fuelEntryOutboxPayload(): String =
        assertNotNull(
            database.databaseQueries.outboxPayloadByEntity(
                entityType = EntityType.FUEL_ENTRY.name,
                entityId = "fuel-entry-1",
            ),
        )

    fun close() = databaseFactory.close()

    private fun fuelEntryRow(vehicleId: String) =
        FuelEntryDatabaseRow(
            id = "fuel-entry-1",
            ownerId = owner.value,
            vehicleId = vehicleId,
            date = 1_100L,
            odometerKm = 20L,
            litersScaled = 50_000L,
            pricePerLiterScaled = 150_000L,
            totalCostMinor = 7_500L,
            currency = "EUR",
            isFullTank = true,
            hasMissedEntries = false,
            odometerInconsistent = false,
            notes = null,
            createdAt = 1_100L,
            updatedAt = 1_100L,
            serverUpdatedAt = null,
            deletedAt = null,
            syncState = "SYNCED",
            localRevision = 1L,
            localMutationSeq = 2L,
            schemaVersion = 1L,
        )
}

private fun buildDirectFuelEntryPayload(
    id: String,
    ownerId: String,
    vehicleId: String,
): String =
    buildJsonObject {
        put("entityType", "FUEL_ENTRY")
        put("id", id)
        put("ownerId", ownerId)
        put("vehicleId", vehicleId)
        put("date", 1_100L)
        put("odometerKm", 20L)
        put("litersScaled", 50_000L)
        put("pricePerLiterScaled", 150_000L)
        put("totalCostMinor", 7_500L)
        put("currency", "EUR")
        put("isFullTank", true)
        put("hasMissedEntries", false)
        put("odometerInconsistent", false)
        put("notes", JsonNull)
        put("createdAt", 1_100L)
        put("updatedAt", 1_100L)
        put("deleted", false)
        put("deletedAt", JsonNull)
        put("schemaVersion", 1L)
    }.toString()

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
