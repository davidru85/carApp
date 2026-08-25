package com.ruizurraca.carapp

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import com.ruizurraca.carapp.core.common.OwnerContext
import com.ruizurraca.carapp.core.database.DatabaseFactory
import com.ruizurraca.carapp.core.model.OwnerId
import com.ruizurraca.carapp.shared.testing.testAppGraphDependencies
import com.ruizurraca.carapp.shared.testing.testAppProviders
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class VehicleFormStateHolderTest {
    @Test
    fun nameIntentUpdatesFormState() {
        val holder = VehicleFormStateHolder(vehicleId = null)

        holder.setName("Roadster")

        assertEquals("Roadster", holder.state.value.name)
    }

    @Test
    fun savePersistsACompletePendingVehicleForTheCurrentOwner() =
        runTest {
            val defaultDependencies = testAppGraphDependencies()
            val database = defaultDependencies.databaseFactory.create()
            val graph =
                buildAppGraph(
                    isDebugBuild = true,
                    providers =
                        testAppProviders(
                            defaultDependencies.copy(
                                databaseFactory = fixedDatabaseFactory(database),
                                ownerContext = fixedOwnerContext(OwnerId("anonymous-user")),
                            ),
                        ),
                )

            try {
                val holder = graph.vehicleFormStateHolder(vehicleId = null)
                holder.setName("Roadster")

                holder.save()

                val vehicle =
                    database.databaseQueries
                        .selectVehicleById("00000000-0000-4000-8000-000000000001")
                        .awaitAsOneOrNull()
                assertNotNull(vehicle)
                assertEquals("anonymous-user", vehicle.ownerId)
                assertEquals("Roadster", vehicle.name)
                assertEquals("roadster", vehicle.nameFold)
                assertEquals(0L, vehicle.initialOdometerKm)
                assertEquals(0L, vehicle.currentOdometerKm)
                assertEquals("GASOLINE", vehicle.fuelType)
                assertEquals("PENDING", vehicle.syncState)
                assertEquals(1L, vehicle.localRevision)
                assertEquals(2L, vehicle.localMutationSeq)
                assertEquals(1L, vehicle.schemaVersion)
                assertEquals(0L, vehicle.deleted)
                assertEquals(null, vehicle.deletedAt)
            } finally {
                graph.close()
            }
        }

    @Test
    fun saveEnqueuesTheClosedRemoteVehicleSnapshot() =
        runTest {
            val defaultDependencies = testAppGraphDependencies()
            val database = defaultDependencies.databaseFactory.create()
            val graph =
                buildAppGraph(
                    isDebugBuild = true,
                    providers =
                        testAppProviders(
                            defaultDependencies.copy(
                                databaseFactory = fixedDatabaseFactory(database),
                                ownerContext = fixedOwnerContext(OwnerId("anonymous-user")),
                            ),
                        ),
                )

            try {
                val holder = graph.vehicleFormStateHolder(vehicleId = null)
                holder.setName("Roadster")

                holder.save()

                val outbox =
                    database.databaseQueries
                        .selectOutboxByEntity(
                            entityType = "VEHICLE",
                            entityId = "00000000-0000-4000-8000-000000000001",
                        ).awaitAsOneOrNull()
                assertNotNull(outbox)
                assertEquals(1L, outbox.localRevision)
                assertEquals(0L, outbox.attemptCount)
                assertEquals(0L, outbox.nextAttemptAt)
                assertEquals(
                    setOf(
                        "id",
                        "ownerId",
                        "name",
                        "initialOdometerKm",
                        "brand",
                        "model",
                        "fuelType",
                        "createdAt",
                        "updatedAt",
                        "deleted",
                        "deletedAt",
                        "schemaVersion",
                    ),
                    Json.parseToJsonElement(outbox.payload).jsonObject.keys,
                )
            } finally {
                graph.close()
            }
        }

    private fun fixedDatabaseFactory(database: com.ruizurraca.carapp.core.database.AppDatabase): DatabaseFactory =
        object : DatabaseFactory {
            override fun create() = database
        }

    private fun fixedOwnerContext(ownerId: OwnerId): OwnerContext =
        object : OwnerContext {
            private val state = MutableStateFlow(ownerId)

            override val current: OwnerId = ownerId

            override fun observe(): Flow<OwnerId> = state
        }
}
