package com.ruizurraca.carapp.feature.fuel.data

import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.ValidationWarning
import com.ruizurraca.carapp.core.model.CurrencyCode
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.OwnerId
import com.ruizurraca.carapp.core.testing.FakeAppClock
import com.ruizurraca.carapp.core.testing.FakeOwnerContext
import com.ruizurraca.carapp.core.testing.FakeUuidGenerator
import com.ruizurraca.carapp.feature.fuel.domain.DefaultCalculateConsumption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FuelEntryTransactionBoundaryTest {
    @Test
    fun createLoadsVehicleAndPreviousEntryThenMutatesInsideOneTransaction() =
        runTest {
            val source = TracingFuelEntryLocalDataSource(previousOdometerKm = 400L)
            val repository = repository(source)

            assertIs<Outcome.Ok<EntityId>>(repository.createFuelEntry(createFuelEntryCommand(odometerKm = 500L)))

            assertEquals(
                listOf(
                    "transaction.begin",
                    "facts.vehicle",
                    "facts.previous",
                    "mutation.insert",
                    "transaction.end",
                ),
                source.trace,
            )
        }

    @Test
    fun unconfirmedWarningMutatesNothingInsideTheTransaction() =
        runTest {
            val source = TracingFuelEntryLocalDataSource(previousOdometerKm = 500L)
            val result = repository(source).createFuelEntry(createFuelEntryCommand(odometerKm = 500L))

            assertIs<ValidationWarning.OdometerInconsistent>(assertIs<Outcome.Err<*>>(result).error)
            assertEquals(
                listOf("transaction.begin", "facts.vehicle", "facts.previous", "transaction.end"),
                source.trace,
            )
        }

    private fun repository(source: FuelEntryLocalDataSource): SqlDelightFuelEntryRepository =
        SqlDelightFuelEntryRepository(
            localDataSource = source,
            ownerContext = FakeOwnerContext(OwnerId("owner-a")),
            clock = FakeAppClock(NOW),
            uuidGenerator = FakeUuidGenerator(),
            calculateConsumption = DefaultCalculateConsumption(),
        )
}

private class TracingFuelEntryLocalDataSource(
    private val previousOdometerKm: Long?,
) : FuelEntryLocalDataSource {
    private val mutableTrace = mutableListOf<String>()
    val trace: List<String> get() = mutableTrace

    override fun observeFuelEntryList(
        ownerId: OwnerId,
        vehicleId: EntityId,
        includeDeleted: Boolean,
    ): Flow<List<LocalFuelEntry>> = flowOf(emptyList())

    override fun observeConsumptionEntries(
        ownerId: OwnerId,
        vehicleId: EntityId,
    ): Flow<List<LocalFuelEntry>> = flowOf(emptyList())

    override suspend fun fuelEntry(
        ownerId: OwnerId,
        id: EntityId,
    ): LocalFuelEntry? = null

    override suspend fun <T> writeTransaction(block: suspend FuelEntryWriteScope.() -> T): T {
        mutableTrace += "transaction.begin"
        return try {
            TracingFuelEntryWriteScope(mutableTrace, previousOdometerKm).block()
        } finally {
            mutableTrace += "transaction.end"
        }
    }
}

private class TracingFuelEntryWriteScope(
    private val trace: MutableList<String>,
    private val previousOdometerKm: Long?,
) : FuelEntryWriteScope {
    override suspend fun vehicle(
        ownerId: OwnerId,
        id: EntityId,
    ): FuelEntryVehicleFacts {
        trace += "facts.vehicle"
        return FuelEntryVehicleFacts(id, 0L, VEHICLE_CREATED_AT, null)
    }

    override suspend fun fuelEntry(
        ownerId: OwnerId,
        id: EntityId,
    ): LocalFuelEntry? = null

    override suspend fun previousActiveFuelEntry(
        vehicleId: EntityId,
        date: kotlin.time.Instant,
        createdAt: kotlin.time.Instant,
        id: EntityId,
        excludedId: EntityId?,
    ): LocalFuelEntry? {
        trace += "facts.previous"
        return previousOdometerKm?.let { localEntryWithOdometer(it) }
    }

    override suspend fun insertFuelEntry(
        entry: LocalFuelEntry,
        outboxPayload: (LocalFuelEntry) -> String?,
    ) {
        trace += "mutation.insert"
    }

    override suspend fun updateFuelEntry(
        entry: LocalFuelEntry,
        outboxPayload: (LocalFuelEntry) -> String?,
    ) = error("unused")

    override suspend fun tombstoneFuelEntry(
        entry: LocalFuelEntry,
        outboxPayload: (LocalFuelEntry) -> String?,
    ) = error("unused")
}

private fun localEntryWithOdometer(odometerKm: Long): LocalFuelEntry =
    LocalFuelEntry(
        id = EntityId(FIRST_ENTRY_ID),
        ownerId = OwnerId("owner-a"),
        vehicleId = EntityId(VEHICLE_ID),
        date = ENTRY_DATE,
        odometerKm = odometerKm,
        litersScaled = 40_000L,
        pricePerLiterScaled = 1_500L,
        totalCostMinor = 6_000L,
        currency = CurrencyCode("EUR"),
        isFullTank = true,
        hasMissedEntries = false,
        odometerInconsistent = false,
        notes = null,
        createdAt = ENTRY_DATE,
        updatedAt = ENTRY_DATE,
        serverUpdatedAt = null,
        deletedAt = null,
        syncState = "SYNCED",
        localRevision = 1L,
        localMutationSeq = 1L,
        schemaVersion = 1L,
    )
