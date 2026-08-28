package com.ruizurraca.carapp.feature.vehicle.data

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import com.ruizurraca.carapp.core.common.Confirmation
import com.ruizurraca.carapp.core.database.DatabaseMutations
import com.ruizurraca.carapp.core.database.Fuel_entry
import com.ruizurraca.carapp.core.database.Outbox
import com.ruizurraca.carapp.core.database.VehicleDatabaseAccess
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.FuelType
import com.ruizurraca.carapp.core.model.LOCAL_OWNER
import com.ruizurraca.carapp.core.model.OwnerId
import com.ruizurraca.carapp.core.testing.FakeAppClock
import com.ruizurraca.carapp.core.testing.FakeOwnerContext
import com.ruizurraca.carapp.core.testing.FakeUuidGenerator
import com.ruizurraca.carapp.core.testing.InMemoryDatabaseFactory
import com.ruizurraca.carapp.feature.vehicle.domain.CreateVehicleCommand
import com.ruizurraca.carapp.feature.vehicle.domain.UpdateVehicleCommand
import kotlin.time.Instant
import com.ruizurraca.carapp.core.database.Vehicle as DatabaseVehicle

internal class VehicleRepositoryTestScope(
    initialOwner: OwnerId = LOCAL_OWNER,
) {
    private val databaseFactory = InMemoryDatabaseFactory()
    val database = databaseFactory.create()
    val ownerContext = FakeOwnerContext(initialOwner)
    val clock = FakeAppClock(NOW)
    val uuidGenerator = FakeUuidGenerator()
    val repository =
        SqlDelightVehicleRepository(
            VehicleDatabaseAccess(database),
            ownerContext,
            clock,
            uuidGenerator,
        )
    private val mutations = DatabaseMutations(database)

    fun close() = databaseFactory.close()

    suspend fun seedVehicle(
        id: String = VEHICLE_ID,
        ownerId: OwnerId = ownerContext.current,
        name: String = "Roadster",
        initialOdometerKm: Long = 10,
        deletedAt: Long? = null,
        outboxPayload: String? = null,
    ) {
        if (deletedAt == null) {
            mutations.insertVehicle(
                id = id,
                ownerId = ownerId.value,
                name = name,
                nameFold = name.lowercase(),
                initialOdometerKm = initialOdometerKm,
                brand = "Acme",
                model = "One",
                fuelType = FuelType.GASOLINE.name,
                createdAt = CREATED_AT,
                updatedAt = CREATED_AT,
                schemaVersion = 1,
                outboxPayload = outboxPayload,
            )
        } else {
            mutations.applyRemoteVehicle(
                id = id,
                ownerId = ownerId.value,
                name = name,
                nameFold = name.lowercase(),
                initialOdometerKm = initialOdometerKm,
                brand = "Acme",
                model = "One",
                fuelType = FuelType.GASOLINE.name,
                createdAt = CREATED_AT,
                updatedAt = deletedAt,
                serverUpdatedAt = deletedAt,
                deletedAt = deletedAt,
                schemaVersion = 1,
            )
        }
    }

    suspend fun seedFuelEntry(
        id: String,
        vehicleId: String = VEHICLE_ID,
        ownerId: OwnerId = ownerContext.current,
        date: Long,
        odometerKm: Long,
    ) {
        mutations.insertFuelEntry(
            id = id,
            ownerId = ownerId.value,
            vehicleId = vehicleId,
            date = date,
            odometerKm = odometerKm,
            litersScaled = 50_000,
            pricePerLiterScaled = 150_000,
            totalCostMinor = 7_500,
            currency = "EUR",
            isFullTank = 1,
            hasMissedEntries = 0,
            notes = null,
            createdAt = date,
            updatedAt = date,
            serverUpdatedAt = date,
            deletedAt = null,
            syncState = "SYNCED",
            localRevision = 1,
            localMutationSeq = 1,
            schemaVersion = 1,
        )
    }

    suspend fun vehicle(id: String = VEHICLE_ID): DatabaseVehicle? =
        database.databaseQueries.selectVehicleById(id).awaitAsOneOrNull()

    suspend fun fuelEntry(id: String): Fuel_entry? = database.databaseQueries.selectFuelEntryById(id).awaitAsOneOrNull()

    suspend fun outbox(
        entityType: String,
        entityId: String,
    ): Outbox? = database.databaseQueries.selectOutboxByEntity(entityType, entityId).awaitAsOneOrNull()
}

internal suspend fun <T> withVehicleRepositoryTestScope(
    initialOwner: OwnerId = LOCAL_OWNER,
    block: suspend VehicleRepositoryTestScope.() -> T,
): T {
    val scope = VehicleRepositoryTestScope(initialOwner)
    return try {
        scope.block()
    } finally {
        scope.close()
    }
}

internal fun createVehicleCommand(
    name: String = "Roadster",
    initialOdometerKm: Long = 10,
    brand: String? = "Acme",
    model: String? = "One",
    fuelType: FuelType = FuelType.GASOLINE,
): CreateVehicleCommand =
    CreateVehicleCommand(
        name = name,
        initialOdometerKm = initialOdometerKm,
        brand = brand,
        model = model,
        fuelType = fuelType,
        confirmations = emptySet<Confirmation>(),
    )

internal fun updateVehicleCommand(
    id: String = VEHICLE_ID,
    name: String = "Roadster Updated",
    initialOdometerKm: Long? = null,
    brand: String? = "Acme",
    model: String? = "Two",
    fuelType: FuelType = FuelType.DIESEL,
): UpdateVehicleCommand =
    UpdateVehicleCommand(
        id = EntityId(id),
        name = name,
        initialOdometerKm = initialOdometerKm,
        brand = brand,
        model = model,
        fuelType = fuelType,
        confirmations = emptySet<Confirmation>(),
    )

internal val NOW: Instant = Instant.fromEpochMilliseconds(2_000)
internal const val CREATED_AT = 1_000L
internal const val VEHICLE_ID = "00000000-0000-4000-8000-000000000001"
internal const val SECOND_VEHICLE_ID = "00000000-0000-4000-8000-000000000002"
internal const val FIRST_FUEL_ENTRY_ID = "00000000-0000-4000-8000-000000000003"
internal const val SECOND_FUEL_ENTRY_ID = "00000000-0000-4000-8000-000000000004"
