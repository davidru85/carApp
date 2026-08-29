package com.ruizurraca.carapp.feature.fuel.data

import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.OwnerContext
import com.ruizurraca.carapp.core.common.PersistenceError
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.OwnerId
import com.ruizurraca.carapp.core.testing.FakeAppClock
import com.ruizurraca.carapp.core.testing.FakeOwnerContext
import com.ruizurraca.carapp.core.testing.FakeUuidGenerator
import com.ruizurraca.carapp.feature.fuel.domain.DefaultCalculateConsumption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FuelEntryRepositoryPersistenceErrorTest {
    private val repository =
        SqlDelightFuelEntryRepository(
            localDataSource = FailingFuelEntryLocalDataSource,
            ownerContext = FakeOwnerContext(OwnerId("owner-a")),
            clock = FakeAppClock(NOW),
            uuidGenerator = FakeUuidGenerator(),
            calculateConsumption = DefaultCalculateConsumption(),
        )

    @Test
    fun writeFailureMapsToTransactionFailed() =
        runTest {
            val result = repository.createFuelEntry(createFuelEntryCommand())

            assertEquals(PersistenceError.TransactionFailed, assertIs<Outcome.Err<*>>(result).error)
        }

    @Test
    fun listObservationFailureMapsToDatabaseUnavailable() =
        runTest {
            val result = repository.observeFuelEntries(EntityId(VEHICLE_ID), false).first()

            assertEquals(PersistenceError.DatabaseUnavailable, assertIs<Outcome.Err<*>>(result).error)
        }

    @Test
    fun consumptionObservationFailureMapsToDatabaseUnavailable() =
        runTest {
            val result = repository.observeConsumption(EntityId(VEHICLE_ID)).first()

            assertEquals(PersistenceError.DatabaseUnavailable, assertIs<Outcome.Err<*>>(result).error)
        }
}

private object FailingFuelEntryLocalDataSource : FuelEntryLocalDataSource {
    override fun observeFuelEntryList(
        ownerId: OwnerId,
        vehicleId: EntityId,
        includeDeleted: Boolean,
    ): Flow<List<LocalFuelEntry>> = flow { throw LocalFailure() }

    override fun observeConsumptionEntries(
        ownerId: OwnerId,
        vehicleId: EntityId,
    ): Flow<List<LocalFuelEntry>> = flow { throw LocalFailure() }

    override suspend fun fuelEntry(
        ownerId: OwnerId,
        id: EntityId,
    ): LocalFuelEntry? = throw LocalFailure()

    override suspend fun <T> writeTransaction(block: suspend FuelEntryWriteScope.() -> T): T = throw LocalFailure()
}

private class LocalFailure : RuntimeException()
