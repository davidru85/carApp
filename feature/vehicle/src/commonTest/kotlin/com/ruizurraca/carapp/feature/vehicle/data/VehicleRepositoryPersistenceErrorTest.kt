package com.ruizurraca.carapp.feature.vehicle.data

import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.PersistenceError
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.LOCAL_OWNER
import com.ruizurraca.carapp.core.model.OwnerId
import com.ruizurraca.carapp.core.testing.FakeAppClock
import com.ruizurraca.carapp.core.testing.FakeOwnerContext
import com.ruizurraca.carapp.core.testing.FakeUuidGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class VehicleRepositoryPersistenceErrorTest {
    private val repository =
        SqlDelightVehicleRepository(
            localDataSource = FailingVehicleLocalDataSource,
            ownerContext = FakeOwnerContext(LOCAL_OWNER),
            clock = FakeAppClock(NOW),
            uuidGenerator = FakeUuidGenerator(),
        )

    @Test
    fun writeFailureMapsToTransactionFailed() =
        runTest {
            val result = repository.createVehicle(createVehicleCommand())

            assertEquals(PersistenceError.TransactionFailed, assertIs<Outcome.Err<*>>(result).error)
        }

    @Test
    fun observationFailureMapsToDatabaseUnavailable() =
        runTest {
            val result = repository.observeVehicles(includeDeleted = false).first()

            assertEquals(PersistenceError.DatabaseUnavailable, assertIs<Outcome.Err<*>>(result).error)
        }
}

private object FailingVehicleLocalDataSource : VehicleLocalDataSource {
    override fun observeVehicles(
        ownerId: OwnerId,
        includeDeleted: Boolean,
    ): Flow<List<LocalVehicle>> = flow { throw LocalFailure() }

    override fun observeVehicle(
        ownerId: OwnerId,
        id: EntityId,
    ): Flow<LocalVehicle?> = flow { throw LocalFailure() }

    override fun observeVehicleEditFacts(
        ownerId: OwnerId,
        id: EntityId,
    ): Flow<LocalVehicleEditFacts?> = flow { throw LocalFailure() }

    override suspend fun <T> writeTransaction(block: suspend VehicleWriteScope.() -> T): T = throw LocalFailure()
}

private class LocalFailure : RuntimeException()
