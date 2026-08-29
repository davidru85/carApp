package com.ruizurraca.carapp.feature.fuel.data

import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.model.ConsumptionReport
import com.ruizurraca.carapp.core.model.CurrencyCode
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.FuelEntry
import com.ruizurraca.carapp.core.model.OwnerId
import com.ruizurraca.carapp.core.testing.FakeAppClock
import com.ruizurraca.carapp.core.testing.FakeOwnerContext
import com.ruizurraca.carapp.core.testing.FakeUuidGenerator
import com.ruizurraca.carapp.feature.fuel.domain.CalculateConsumption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FuelEntryRepositoryConsumptionFilterTest {
    @Test
    fun productionRepositoryPassesOnlyActiveRowsForTheRequestedVehicleToCalculation() =
        runTest {
            val requested = localEntry(FIRST_ENTRY_ID, VEHICLE_ID, deleted = false)
            val deleted = localEntry(SECOND_ENTRY_ID, VEHICLE_ID, deleted = true)
            val otherVehicle = localEntry(THIRD_ENTRY_ID, SECOND_VEHICLE_ID, deleted = false)
            val calculator = RecordingCalculator()
            val source = RecordingConsumptionSource(listOf(requested, deleted, otherVehicle))
            val repository = repository(source, calculator)

            assertIs<Outcome.Ok<ConsumptionReport>>(
                repository.observeConsumption(EntityId(VEHICLE_ID)).first(),
            )

            assertEquals(listOf(requested.toDomainFuelEntry()), calculator.received)
        }

    @Test
    fun observeConsumptionUsesTheDedicatedProjectionInsteadOfTheUiListSource() =
        runTest {
            val source = RecordingConsumptionSource(listOf(localEntry(FIRST_ENTRY_ID, VEHICLE_ID, false)))
            val repository = repository(source, RecordingCalculator())

            repository.observeConsumption(EntityId(VEHICLE_ID)).first()

            assertEquals(0, source.listCalls)
            assertEquals(1, source.consumptionCalls)
        }

    private fun repository(
        source: FuelEntryLocalDataSource,
        calculator: CalculateConsumption,
    ): SqlDelightFuelEntryRepository =
        SqlDelightFuelEntryRepository(
            localDataSource = source,
            ownerContext = FakeOwnerContext(OwnerId("owner-a")),
            clock = FakeAppClock(NOW),
            uuidGenerator = FakeUuidGenerator(),
            calculateConsumption = calculator,
        )
}

private class RecordingCalculator : CalculateConsumption {
    var received: List<FuelEntry> = emptyList()

    override fun invoke(entries: List<FuelEntry>): ConsumptionReport {
        received = entries
        return ConsumptionReport(emptyList(), 0, null, false)
    }
}

private class RecordingConsumptionSource(
    private val rows: List<LocalFuelEntry>,
) : FuelEntryLocalDataSource {
    var listCalls = 0
    var consumptionCalls = 0

    override fun observeFuelEntryList(
        ownerId: OwnerId,
        vehicleId: EntityId,
        includeDeleted: Boolean,
    ): Flow<List<LocalFuelEntry>> {
        listCalls += 1
        return flowOf(rows)
    }

    override fun observeConsumptionEntries(
        ownerId: OwnerId,
        vehicleId: EntityId,
    ): Flow<List<LocalFuelEntry>> {
        consumptionCalls += 1
        return flowOf(rows)
    }

    override suspend fun fuelEntry(
        ownerId: OwnerId,
        id: EntityId,
    ): LocalFuelEntry? = null

    override suspend fun <T> writeTransaction(block: suspend FuelEntryWriteScope.() -> T): T = error("unused")
}

private fun localEntry(
    id: String,
    vehicleId: String,
    deleted: Boolean,
): LocalFuelEntry =
    LocalFuelEntry(
        id = EntityId(id),
        ownerId = OwnerId("owner-a"),
        vehicleId = EntityId(vehicleId),
        date = ENTRY_DATE,
        odometerKm = 100L,
        litersScaled = 10_000L,
        pricePerLiterScaled = 1_500L,
        totalCostMinor = 1_500L,
        currency = CurrencyCode("EUR"),
        isFullTank = true,
        hasMissedEntries = false,
        odometerInconsistent = false,
        notes = null,
        createdAt = ENTRY_DATE,
        updatedAt = ENTRY_DATE,
        serverUpdatedAt = null,
        deletedAt = if (deleted) NOW else null,
        syncState = "PENDING",
        localRevision = 1L,
        localMutationSeq = 1L,
        schemaVersion = 1L,
    )
