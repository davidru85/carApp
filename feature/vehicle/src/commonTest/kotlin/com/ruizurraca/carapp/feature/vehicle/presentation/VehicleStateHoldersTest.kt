package com.ruizurraca.carapp.feature.vehicle.presentation

import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.SyncStatus
import com.ruizurraca.carapp.core.common.UiMessageKind
import com.ruizurraca.carapp.core.common.ValidationError
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.FuelType
import com.ruizurraca.carapp.core.model.OwnerId
import com.ruizurraca.carapp.core.model.Vehicle
import com.ruizurraca.carapp.core.testing.TestDispatcherProvider
import com.ruizurraca.carapp.feature.vehicle.domain.CreateVehicleCommand
import com.ruizurraca.carapp.feature.vehicle.domain.UpdateVehicleCommand
import com.ruizurraca.carapp.feature.vehicle.domain.VehicleEditFacts
import com.ruizurraca.carapp.feature.vehicle.domain.VehicleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class VehicleStateHoldersTest {
    @Test
    fun listObservesActiveVehiclesOnlyAndNeverPublishesTombstones() =
        runTest {
            val repository = FakeVehicleRepository()
            val holder =
                VehicleListStateHolder(
                    scope = backgroundScope,
                    repository = repository,
                    dispatchers = TestDispatcherProvider(),
                    refreshVehicles = { Outcome.Ok(Unit) },
                )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { holder.state.collect() }

            repository.vehicles.value = Outcome.Ok(listOf(vehicle(), vehicle(deleted = true)))
            advanceUntilIdle()

            assertEquals(listOf(false), repository.includeDeletedArguments)
            assertEquals(
                listOf(VEHICLE_ID),
                holder.state.value.vehicles
                    .map { it.id },
            )
            assertEquals(SyncStatus.Idle, holder.state.value.syncStatus)
            assertFalse(holder.state.value.isLoading)
            holder.close()
        }

    @Test
    fun createRoundTripsFuelTypeAndPublishesTheCreatedVehicleId() =
        runTest {
            val repository = FakeVehicleRepository()
            var captured: CreateVehicleCommand? = null
            val holder =
                VehicleFormStateHolder(
                    scope = backgroundScope,
                    vehicleId = null,
                    repository = repository,
                    dispatchers = TestDispatcherProvider(),
                    createVehicle = { command ->
                        captured = command
                        Outcome.Ok(EntityId(VEHICLE_ID))
                    },
                    updateVehicle = { Outcome.Ok(Unit) },
                )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { holder.state.collect() }

            holder.setName("Roadster")
            holder.setInitialOdometerKm(125)
            holder.setBrand("Acme")
            holder.setModel("One")
            holder.setFuelType(FuelType.DIESEL)
            holder.save()
            advanceUntilIdle()

            assertEquals(FuelType.DIESEL, requireNotNull(captured).fuelType)
            assertEquals(125, requireNotNull(captured).initialOdometerKm)
            assertEquals(VEHICLE_ID, holder.state.value.vehicleId)
            assertFalse(holder.state.value.isSaving)
            assertNull(holder.state.value.message)
            holder.close()
        }

    @Test
    fun editabilityReactsToFactsAndAStaleTrueStillReportsTheWriteRejection() =
        runTest {
            val repository = FakeVehicleRepository()
            repository.editFacts.value = Outcome.Ok(VehicleEditFacts(vehicle(), true))
            val holder =
                VehicleFormStateHolder(
                    scope = backgroundScope,
                    vehicleId = VEHICLE_ID,
                    repository = repository,
                    dispatchers = TestDispatcherProvider(),
                    createVehicle = { Outcome.Ok(EntityId(VEHICLE_ID)) },
                    updateVehicle = {
                        Outcome.Err(ValidationError.EditNotAllowed("initialOdometerKm"))
                    },
                )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { holder.state.collect() }
            advanceUntilIdle()
            assertTrue(holder.state.value.canEditInitialOdometer)

            holder.setInitialOdometerKm(200)
            holder.save()
            advanceUntilIdle()

            assertEquals(
                ValidationError.EditNotAllowed("initialOdometerKm").code,
                holder.state.value.message
                    ?.code,
            )
            assertEquals(
                UiMessageKind.ERROR,
                holder.state.value.message
                    ?.kind,
            )
            assertFalse(holder.state.value.isSaving)

            repository.editFacts.value = Outcome.Ok(VehicleEditFacts(vehicle(), false))
            advanceUntilIdle()
            assertFalse(holder.state.value.canEditInitialOdometer)
            holder.close()
        }
}

private class FakeVehicleRepository : VehicleRepository {
    val vehicles = MutableStateFlow<Outcome<List<Vehicle>, AppError>>(Outcome.Ok(emptyList()))
    val editFacts = MutableStateFlow<Outcome<VehicleEditFacts?, AppError>>(Outcome.Ok(null))
    val includeDeletedArguments = mutableListOf<Boolean>()

    override fun observeVehicles(includeDeleted: Boolean): Flow<Outcome<List<Vehicle>, AppError>> {
        includeDeletedArguments += includeDeleted
        return vehicles
    }

    override fun observeVehicle(id: EntityId): Flow<Outcome<Vehicle?, AppError>> = MutableStateFlow(Outcome.Ok(null))

    override fun observeVehicleEditFacts(id: EntityId): Flow<Outcome<VehicleEditFacts?, AppError>> = editFacts

    override suspend fun createVehicle(command: CreateVehicleCommand): Outcome<EntityId, AppError> =
        Outcome.Ok(EntityId(VEHICLE_ID))

    override suspend fun updateVehicle(command: UpdateVehicleCommand): Outcome<Unit, AppError> = Outcome.Ok(Unit)

    override suspend fun deleteVehicle(id: EntityId): Outcome<Unit, AppError> = Outcome.Ok(Unit)
}

private fun vehicle(deleted: Boolean = false): Vehicle =
    Vehicle(
        id = EntityId(if (deleted) DELETED_VEHICLE_ID else VEHICLE_ID),
        ownerId = OwnerId("owner-a"),
        name = if (deleted) "Deleted" else "Roadster",
        initialOdometerKm = 100,
        currentOdometerKm = 150,
        brand = "Acme",
        model = "One",
        fuelType = FuelType.GASOLINE,
        createdAt = Instant.fromEpochMilliseconds(1_000),
        updatedAt = Instant.fromEpochMilliseconds(2_000),
        deletedAt = if (deleted) Instant.fromEpochMilliseconds(3_000) else null,
    )

private const val VEHICLE_ID = "00000000-0000-4000-8000-000000000001"
private const val DELETED_VEHICLE_ID = "00000000-0000-4000-8000-000000000002"
