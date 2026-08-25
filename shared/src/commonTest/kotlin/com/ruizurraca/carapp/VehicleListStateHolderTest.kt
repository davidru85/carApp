package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.common.OwnerContext
import com.ruizurraca.carapp.core.database.DatabaseFactory
import com.ruizurraca.carapp.core.model.FuelType
import com.ruizurraca.carapp.core.model.LOCAL_OWNER
import com.ruizurraca.carapp.core.model.OwnerId
import com.ruizurraca.carapp.shared.testing.testAppGraphDependencies
import com.ruizurraca.carapp.shared.testing.testAppProviders
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class VehicleListStateHolderTest {
    @Test
    fun listPublishesVehiclesPersistedThroughTheSharedForm() =
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
                                ownerContext = fixedOwnerContext(LOCAL_OWNER),
                            ),
                        ),
                )

            try {
                val list = graph.vehicleListStateHolder()
                val form = graph.vehicleFormStateHolder(vehicleId = null)
                form.setName("Roadster")

                form.save()
                form.state.first { state -> !state.isSaving }
                val publishedState = list.state.first { state -> state.vehicles.isNotEmpty() }

                assertEquals(
                    listOf(
                        VehicleListItemUi(
                            id = "00000000-0000-4000-8000-000000000001",
                            name = "Roadster",
                            currentOdometerKm = 0L,
                            fuelType = FuelType.GASOLINE,
                            deleted = false,
                        ),
                    ),
                    publishedState.vehicles,
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
