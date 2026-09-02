package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.database.FuelEntryDatabaseAccess
import com.ruizurraca.carapp.core.database.VehicleDatabaseAccess
import com.ruizurraca.carapp.core.database.outboxPayloadByEntity
import com.ruizurraca.carapp.core.model.CurrencyCode
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.FuelType
import com.ruizurraca.carapp.core.model.OwnerId
import com.ruizurraca.carapp.core.sync.EntityType
import com.ruizurraca.carapp.core.testing.FakeAppClock
import com.ruizurraca.carapp.core.testing.FakeOwnerContext
import com.ruizurraca.carapp.core.testing.FakeUuidGenerator
import com.ruizurraca.carapp.core.testing.InMemoryDatabaseFactory
import com.ruizurraca.carapp.feature.fuel.data.SqlDelightFuelEntryRepository
import com.ruizurraca.carapp.feature.fuel.domain.CreateFuelEntryCommand
import com.ruizurraca.carapp.feature.fuel.domain.MoneyInput
import com.ruizurraca.carapp.feature.vehicle.data.SqlDelightVehicleRepository
import com.ruizurraca.carapp.feature.vehicle.domain.CreateVehicleCommand
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Instant

/**
 * Cross-feature coalescence parity test: proves the direct Fuel Entry writer
 * (`SqlDelightFuelEntryRepository`) and the Vehicle cascade-delete writer
 * (`SqlDelightVehicleRepository`) produce outbox payloads with the same canonical key set.
 * Lives in `:shared` because it depends on both `:feature:vehicle` and `:feature:fuel`,
 * respecting the "features do not depend on other features" architecture rule.
 */
class OutboxCoalescenceParityTest {
    @Test
    fun directFuelEntryWriteThenCascadeDeleteProducesTheSameCanonicalKeySet() =
        runTest {
            val fixture = CoalescenceFixture()
            try {
                val vehicleId = fixture.createVehicle()
                val fuelEntryId = fixture.createFuelEntry(vehicleId)

                val directPayload = fixture.fuelEntryOutboxPayload(fuelEntryId)
                val directKeys = Json.parseToJsonElement(directPayload).jsonObject.keys
                assertEquals(CANONICAL_FUEL_ENTRY_PAYLOAD_KEYS, directKeys)

                fixture.deleteVehicle(vehicleId)

                val cascadePayload = fixture.fuelEntryOutboxPayload(fuelEntryId)
                val cascadeJson = Json.parseToJsonElement(cascadePayload).jsonObject

                assertEquals(directKeys, cascadeJson.keys)
                assertEquals(CANONICAL_FUEL_ENTRY_PAYLOAD_KEYS, cascadeJson.keys)
                assertEquals("FUEL_ENTRY", cascadeJson.getValue("entityType").toString().trim('"'))
            } finally {
                fixture.close()
            }
        }

    @Test
    fun cascadeDeleteThenSubsequentFuelEntryCoalescenceRetainsCanonicalKeySet() =
        runTest {
            val fixture = CoalescenceFixture()
            try {
                val vehicleId = fixture.createVehicle()
                val fuelEntryId = fixture.createFuelEntry(vehicleId)

                fixture.deleteVehicle(vehicleId)

                val cascadePayload = fixture.fuelEntryOutboxPayload(fuelEntryId)
                val cascadeJson = Json.parseToJsonElement(cascadePayload).jsonObject
                assertEquals(CANONICAL_FUEL_ENTRY_PAYLOAD_KEYS, cascadeJson.keys)
                assertEquals("FUEL_ENTRY", cascadeJson.getValue("entityType").toString().trim('"'))

                fixture.reapplyDirectFuelEntryOutbox(fuelEntryId, cascadePayload)

                val coalescedPayload = fixture.fuelEntryOutboxPayload(fuelEntryId)
                val coalescedJson = Json.parseToJsonElement(coalescedPayload).jsonObject
                assertEquals(CANONICAL_FUEL_ENTRY_PAYLOAD_KEYS, coalescedJson.keys)
                assertEquals("FUEL_ENTRY", coalescedJson.getValue("entityType").toString().trim('"'))
            } finally {
                fixture.close()
            }
        }
}

private class CoalescenceFixture {
    private val databaseFactory = InMemoryDatabaseFactory()
    private val databaseHandle = databaseFactory.create()
    private val database = databaseHandle.database
    private val owner = OwnerId("owner-a")
    private val clock = FakeAppClock(Instant.fromEpochMilliseconds(2_000))
    private val ownerContext = FakeOwnerContext(owner)

    private val vehicleRepository =
        SqlDelightVehicleRepository(
            VehicleDatabaseAccess(database),
            ownerContext,
            clock,
            FakeUuidGenerator(),
        )

    private val fuelEntryRepository =
        SqlDelightFuelEntryRepository(
            FuelEntryDatabaseAccess(database),
            ownerContext,
            clock,
            FakeUuidGenerator(),
        )

    suspend fun createVehicle(): String =
        vehicleRepository
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

    suspend fun createFuelEntry(vehicleId: String): String =
        fuelEntryRepository
            .createFuelEntry(
                CreateFuelEntryCommand(
                    vehicleId = EntityId(vehicleId),
                    date = Instant.fromEpochMilliseconds(1_100),
                    odometerKm = 20,
                    money = MoneyInput.LitersAndPrice(litersScaled = 50_000, pricePerLiterScaled = 150_000),
                    currency = CurrencyCode("EUR"),
                    isFullTank = true,
                    hasMissedEntries = false,
                    notes = null,
                    confirmations = emptySet(),
                ),
            ).let { it as Outcome.Ok }
            .value.value

    suspend fun deleteVehicle(vehicleId: String) {
        vehicleRepository.deleteVehicle(EntityId(vehicleId))
    }

    suspend fun fuelEntryOutboxPayload(fuelEntryId: String): String =
        assertNotNull(
            database.databaseQueries.outboxPayloadByEntity(
                entityType = EntityType.FUEL_ENTRY.name,
                entityId = fuelEntryId,
            ),
        )

    /**
     * After a cascade delete, the Fuel Entry is tombstoned and cannot be written through
     * `FuelEntryRepository` (it would return `EntityDeleted`). This re-applies a previously
     * produced conformant payload through the lowest applicable coalescence API to prove the
     * coalesced row retains a canonical key set regardless of the last writer.
     */
    suspend fun reapplyDirectFuelEntryOutbox(
        fuelEntryId: String,
        payload: String,
    ) {
        database.databaseQueries.coalesceOutbox(
            entityType = EntityType.FUEL_ENTRY.name,
            entityId = fuelEntryId,
            payload = payload,
            localRevision = 99L,
        )
    }

    fun close() = databaseFactory.close()
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
