package com.ruizurraca.carapp.feature.fuel.data

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import com.ruizurraca.carapp.core.common.Confirmation
import com.ruizurraca.carapp.core.database.DatabaseMutations
import com.ruizurraca.carapp.core.database.FuelEntryDatabaseAccess
import com.ruizurraca.carapp.core.database.Fuel_entry
import com.ruizurraca.carapp.core.database.Outbox
import com.ruizurraca.carapp.core.model.CurrencyCode
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.FuelType
import com.ruizurraca.carapp.core.model.LOCAL_OWNER
import com.ruizurraca.carapp.core.model.OwnerId
import com.ruizurraca.carapp.core.testing.FakeAppClock
import com.ruizurraca.carapp.core.testing.FakeOwnerContext
import com.ruizurraca.carapp.core.testing.FakeUuidGenerator
import com.ruizurraca.carapp.core.testing.InMemoryDatabaseFactory
import com.ruizurraca.carapp.feature.fuel.domain.CreateFuelEntryCommand
import com.ruizurraca.carapp.feature.fuel.domain.MoneyInput
import com.ruizurraca.carapp.feature.fuel.domain.UpdateFuelEntryCommand
import kotlin.time.Instant

internal class FuelEntryRepositoryTestScope(
    initialOwner: OwnerId = LOCAL_OWNER,
) {
    private val databaseFactory = InMemoryDatabaseFactory()
    val database = databaseFactory.create()
    val ownerContext = FakeOwnerContext(initialOwner)
    val clock = FakeAppClock(NOW)
    val uuidGenerator = FakeUuidGenerator()
    val repository =
        SqlDelightFuelEntryRepository(
            FuelEntryDatabaseAccess(database),
            ownerContext,
            clock,
            uuidGenerator,
        )
    val localDataSource = SqlDelightFuelEntryLocalDataSource(FuelEntryDatabaseAccess(database))
    private val mutations = DatabaseMutations(database)

    fun close() = databaseFactory.close()

    suspend fun seedVehicle(
        id: String = VEHICLE_ID,
        ownerId: OwnerId = ownerContext.current,
        initialOdometerKm: Long = 0L,
        createdAt: Instant = VEHICLE_CREATED_AT,
        deletedAt: Instant? = null,
    ) {
        if (deletedAt == null) {
            mutations.insertVehicle(
                id = id,
                ownerId = ownerId.value,
                name = "Roadster-$id",
                nameFold = "roadster-$id",
                initialOdometerKm = initialOdometerKm,
                brand = "Acme",
                model = "One",
                fuelType = FuelType.GASOLINE.name,
                createdAt = createdAt.toEpochMilliseconds(),
                updatedAt = createdAt.toEpochMilliseconds(),
                schemaVersion = 1L,
                outboxPayload = null,
            )
        } else {
            mutations.applyRemoteVehicle(
                id = id,
                ownerId = ownerId.value,
                name = "Roadster-$id",
                nameFold = "roadster-$id",
                initialOdometerKm = initialOdometerKm,
                brand = "Acme",
                model = "One",
                fuelType = FuelType.GASOLINE.name,
                createdAt = createdAt.toEpochMilliseconds(),
                updatedAt = deletedAt.toEpochMilliseconds(),
                serverUpdatedAt = deletedAt.toEpochMilliseconds(),
                deletedAt = deletedAt.toEpochMilliseconds(),
                schemaVersion = 1L,
            )
        }
    }

    @Suppress("LongParameterList")
    suspend fun seedFuelEntry(
        id: String,
        ownerId: OwnerId = ownerContext.current,
        vehicleId: String = VEHICLE_ID,
        date: Instant,
        odometerKm: Long,
        litersScaled: Long = 40_000L,
        pricePerLiterScaled: Long = 1_500L,
        totalCostMinor: Long = 6_000L,
        isFullTank: Boolean = true,
        hasMissedEntries: Boolean = false,
        notes: String? = null,
        createdAt: Instant = date,
        deletedAt: Instant? = null,
        syncState: String = "SYNCED",
        localRevision: Long = 1L,
        localMutationSeq: Long = 1L,
    ) {
        mutations.insertFuelEntry(
            id = id,
            ownerId = ownerId.value,
            vehicleId = vehicleId,
            date = date.toEpochMilliseconds(),
            odometerKm = odometerKm,
            litersScaled = litersScaled,
            pricePerLiterScaled = pricePerLiterScaled,
            totalCostMinor = totalCostMinor,
            currency = "EUR",
            isFullTank = if (isFullTank) 1L else 0L,
            hasMissedEntries = if (hasMissedEntries) 1L else 0L,
            notes = notes,
            createdAt = createdAt.toEpochMilliseconds(),
            updatedAt = createdAt.toEpochMilliseconds(),
            serverUpdatedAt = createdAt.toEpochMilliseconds(),
            deletedAt = deletedAt?.toEpochMilliseconds(),
            syncState = syncState,
            localRevision = localRevision,
            localMutationSeq = localMutationSeq,
            schemaVersion = 1L,
        )
    }

    suspend fun fuelEntry(id: String): Fuel_entry? = database.databaseQueries.selectFuelEntryById(id).awaitAsOneOrNull()

    suspend fun outbox(id: String): Outbox? =
        database.databaseQueries.selectOutboxByEntity("FUEL_ENTRY", id).awaitAsOneOrNull()
}

internal suspend fun <T> withFuelEntryRepositoryTestScope(
    initialOwner: OwnerId = LOCAL_OWNER,
    block: suspend FuelEntryRepositoryTestScope.() -> T,
): T {
    val scope = FuelEntryRepositoryTestScope(initialOwner)
    return try {
        scope.block()
    } finally {
        scope.close()
    }
}

internal fun createFuelEntryCommand(
    vehicleId: String = VEHICLE_ID,
    date: Instant = ENTRY_DATE,
    odometerKm: Long = 500L,
    litersScaled: Long = 40_000L,
    pricePerLiterScaled: Long = 1_500L,
    isFullTank: Boolean = true,
    hasMissedEntries: Boolean = false,
    notes: String? = " note ",
    confirmations: Set<Confirmation> = emptySet(),
): CreateFuelEntryCommand =
    CreateFuelEntryCommand(
        vehicleId = EntityId(vehicleId),
        date = date,
        odometerKm = odometerKm,
        money = MoneyInput.LitersAndPrice(litersScaled, pricePerLiterScaled),
        currency = CurrencyCode("EUR"),
        isFullTank = isFullTank,
        hasMissedEntries = hasMissedEntries,
        notes = notes,
        confirmations = confirmations,
    )

internal fun updateFuelEntryCommand(
    id: String = FIRST_ENTRY_ID,
    vehicleId: String = VEHICLE_ID,
    date: Instant = ENTRY_DATE,
    odometerKm: Long = 500L,
    litersScaled: Long = 40_000L,
    pricePerLiterScaled: Long = 1_500L,
    isFullTank: Boolean = true,
    hasMissedEntries: Boolean = false,
    notes: String? = " updated ",
    confirmations: Set<Confirmation> = emptySet(),
): UpdateFuelEntryCommand =
    UpdateFuelEntryCommand(
        id = EntityId(id),
        vehicleId = EntityId(vehicleId),
        date = date,
        odometerKm = odometerKm,
        money = MoneyInput.LitersAndPrice(litersScaled, pricePerLiterScaled),
        currency = CurrencyCode("EUR"),
        isFullTank = isFullTank,
        hasMissedEntries = hasMissedEntries,
        notes = notes,
        confirmations = confirmations,
    )

internal val NOW: Instant = Instant.parse("2026-08-29T12:00:00Z")
internal val ENTRY_DATE: Instant = Instant.parse("2026-08-29T11:00:00Z")
internal val VEHICLE_CREATED_AT: Instant = Instant.parse("2026-01-15T10:00:00Z")
internal const val VEHICLE_ID = "00000000-0000-4000-8000-000000000100"
internal const val SECOND_VEHICLE_ID = "00000000-0000-4000-8000-000000000200"
internal const val FIRST_ENTRY_ID = "00000000-0000-4000-8000-000000000101"
internal const val SECOND_ENTRY_ID = "00000000-0000-4000-8000-000000000102"
internal const val THIRD_ENTRY_ID = "00000000-0000-4000-8000-000000000103"
internal const val ORPHAN_ENTRY_ID = "00000000-0000-4000-8000-000000000999"
