package com.ruizurraca.carapp.feature.vehicle.data

import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.database.FuelEntryDatabaseRow
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.LOCAL_OWNER
import com.ruizurraca.carapp.core.model.OwnerId
import com.ruizurraca.carapp.core.testing.FakeAppClock
import com.ruizurraca.carapp.core.testing.FakeOwnerContext
import com.ruizurraca.carapp.core.testing.FakeUuidGenerator
import com.ruizurraca.carapp.feature.vehicle.domain.VehicleNameCandidate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class VehicleTransactionBoundaryTest {
    @Test
    fun createLoadsFactsValidatesAndMutatesInsideOneWriteTransaction() =
        runTest {
            val dataSource = RecordingVehicleLocalDataSource()
            val repository =
                SqlDelightVehicleRepository(
                    localDataSource = dataSource,
                    ownerContext = FakeOwnerContext(LOCAL_OWNER),
                    clock = FakeAppClock(NOW),
                    uuidGenerator = FakeUuidGenerator(),
                )

            val result = repository.createVehicle(createVehicleCommand())

            assertIs<Outcome.Ok<EntityId>>(result)
            assertEquals(
                listOf("transaction.begin", "facts.activeVehicles", "mutation.insert", "transaction.commit"),
                dataSource.trace,
            )
            assertTrue(dataSource.allOperationsWereInsideTransaction)
        }
}

private class RecordingVehicleLocalDataSource : VehicleLocalDataSource {
    private var inTransaction = false
    private var operationsInsideTransaction = true
    private val mutableTrace = mutableListOf<String>()
    val trace: List<String> get() = mutableTrace.toList()
    val allOperationsWereInsideTransaction: Boolean get() = operationsInsideTransaction

    private val scope =
        object : VehicleWriteScope {
            override suspend fun activeVehicleCandidates(ownerId: OwnerId): List<VehicleNameCandidate> {
                record("facts.activeVehicles")
                return emptyList()
            }

            override suspend fun vehicle(
                ownerId: OwnerId,
                id: EntityId,
            ): LocalVehicle? {
                record("facts.vehicle")
                return null
            }

            override suspend fun hasActiveFuelEntries(id: EntityId): Boolean {
                record("facts.fuelEntries")
                return false
            }

            override suspend fun insertVehicle(
                vehicle: LocalVehicle,
                outboxPayload: String?,
            ) = record("mutation.insert")

            override suspend fun updateVehicle(
                vehicle: LocalVehicle,
                outboxPayload: String?,
            ) = record("mutation.update")

            override suspend fun tombstoneVehicleCascade(
                vehicle: LocalVehicle,
                vehicleOutboxPayload: String?,
                fuelEntryOutboxPayload: (FuelEntryDatabaseRow) -> String?,
            ) = record("mutation.delete")
        }

    override fun observeVehicles(
        ownerId: OwnerId,
        includeDeleted: Boolean,
    ): Flow<List<LocalVehicle>> = flowOf(emptyList())

    override fun observeVehicle(
        ownerId: OwnerId,
        id: EntityId,
    ): Flow<LocalVehicle?> = flowOf(null)

    override fun observeVehicleEditFacts(
        ownerId: OwnerId,
        id: EntityId,
    ): Flow<LocalVehicleEditFacts?> = flowOf(null)

    override suspend fun <T> writeTransaction(block: suspend VehicleWriteScope.() -> T): T {
        mutableTrace += "transaction.begin"
        inTransaction = true
        return try {
            scope.block()
        } finally {
            inTransaction = false
            mutableTrace += "transaction.commit"
        }
    }

    private fun record(event: String) {
        operationsInsideTransaction = operationsInsideTransaction && inTransaction
        mutableTrace += event
    }
}
