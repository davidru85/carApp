package com.ruizurraca.carapp.feature.vehicle.presentation

import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.OwnerContext
import com.ruizurraca.carapp.core.common.PersistenceError
import com.ruizurraca.carapp.core.common.SyncStatus
import com.ruizurraca.carapp.core.common.UiMessageKind
import com.ruizurraca.carapp.core.common.ValidationError
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.FuelType
import com.ruizurraca.carapp.core.model.LOCAL_OWNER
import com.ruizurraca.carapp.core.model.OwnerId
import com.ruizurraca.carapp.core.model.Vehicle
import com.ruizurraca.carapp.core.testing.TestDispatcherProvider
import com.ruizurraca.carapp.feature.vehicle.domain.CreateVehicleCommand
import com.ruizurraca.carapp.feature.vehicle.domain.UpdateVehicleCommand
import com.ruizurraca.carapp.feature.vehicle.domain.VehicleEditFacts
import com.ruizurraca.carapp.feature.vehicle.domain.VehicleRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
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

@OptIn(ExperimentalCoroutinesApi::class)
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
                    ownerContext = FakeOwnerContext(LOCAL_OWNER),
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
            assertNull(holder.state.value.vehicleId)
            assertEquals(VEHICLE_ID, holder.state.value.savedVehicleId)
            assertFalse(holder.state.value.isSaving)
            assertNull(holder.state.value.message)
            holder.close()
        }

    @Test
    fun successfulCreateResetsThePublicFormAndTheNextSaveCreatesAnotherVehicle() =
        runTest {
            val repository = FakeVehicleRepository()
            val createCommands = mutableListOf<CreateVehicleCommand>()
            val updateCommands = mutableListOf<UpdateVehicleCommand>()
            val createdIds = ArrayDeque(listOf(EntityId(VEHICLE_ID), EntityId(SECOND_VEHICLE_ID)))
            val holder =
                createVehicleFormStateHolder(
                    scope = backgroundScope,
                    vehicleId = null,
                    repository = repository,
                    dispatchers = TestDispatcherProvider(),
                    createVehicle = { command ->
                        createCommands += command
                        Outcome.Ok(createdIds.removeFirst())
                    },
                    updateVehicle = { command ->
                        updateCommands += command
                        Outcome.Ok(Unit)
                    },
                )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { holder.state.collect() }

            holder.setName("First vehicle")
            holder.setInitialOdometerKm(125)
            holder.save()
            advanceUntilIdle()
            val stateBeforeSecondCreate = holder.state.value

            holder.setName("Second vehicle")
            holder.setInitialOdometerKm(250)
            holder.save()
            advanceUntilIdle()

            assertNull(stateBeforeSecondCreate.vehicleId)
            assertEquals("", stateBeforeSecondCreate.name)
            assertEquals(0, stateBeforeSecondCreate.initialOdometerKm)
            assertEquals(
                listOf("First vehicle", "Second vehicle"),
                createCommands.map(CreateVehicleCommand::name),
            )
            assertEquals(emptyList(), updateCommands)
            assertEquals(SECOND_VEHICLE_ID, holder.state.value.savedVehicleId)
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

    @Test
    fun editsBeforeFirstFactsSurviveAndTheUpdateMatchesTheDisplayedDraft() =
        runTest {
            val repository = FakeVehicleRepository()
            var captured: UpdateVehicleCommand? = null
            val holder =
                VehicleFormStateHolder(
                    scope = backgroundScope,
                    vehicleId = VEHICLE_ID,
                    repository = repository,
                    dispatchers = TestDispatcherProvider(),
                    createVehicle = { Outcome.Ok(EntityId(VEHICLE_ID)) },
                    updateVehicle = { command ->
                        captured = command
                        Outcome.Ok(Unit)
                    },
                )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { holder.state.collect() }

            holder.setName("Edited roadster")
            holder.setInitialOdometerKm(321)
            holder.setBrand("Edited brand")
            holder.setModel("Edited model")
            repository.editFacts.value = Outcome.Ok(VehicleEditFacts(vehicle(), true))
            advanceUntilIdle()

            assertEquals("Edited roadster", holder.state.value.name)
            assertEquals(321, holder.state.value.initialOdometerKm)
            assertEquals("Edited brand", holder.state.value.brand)
            assertEquals("Edited model", holder.state.value.model)
            assertTrue(holder.state.value.canEditInitialOdometer)

            holder.save()
            advanceUntilIdle()

            assertEquals("Edited roadster", requireNotNull(captured).name)
            assertEquals(321, requireNotNull(captured).initialOdometerKm)
            assertEquals("Edited brand", requireNotNull(captured).brand)
            assertEquals("Edited model", requireNotNull(captured).model)
            holder.close()
        }

    @Test
    fun listLoadingMeansTheVehicleListIsUnknownAndARefreshNeverReopensIt() =
        runTest {
            val observedVehicles = MutableSharedFlow<Outcome<List<Vehicle>, AppError>>(replay = 1)
            val repository = FakeVehicleRepository(observedVehicles = observedVehicles)
            val refreshGate = CompletableDeferred<Unit>()
            val holder =
                VehicleListStateHolder(
                    scope = backgroundScope,
                    repository = repository,
                    dispatchers = TestDispatcherProvider(),
                    refreshVehicles = {
                        refreshGate.await()
                        Outcome.Ok(Unit)
                    },
                    ownerContext = FakeOwnerContext(LOCAL_OWNER),
                )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { holder.state.collect() }
            advanceUntilIdle()

            assertTrue(holder.state.value.isLoading)

            observedVehicles.emit(Outcome.Ok(listOf(vehicle())))
            advanceUntilIdle()

            assertFalse(holder.state.value.isLoading)

            holder.refresh()
            advanceUntilIdle()

            assertFalse(holder.state.value.isLoading)

            refreshGate.complete(Unit)
            advanceUntilIdle()

            assertFalse(holder.state.value.isLoading)
            holder.close()
        }

    @Test
    fun anEmptyResultForOneOwnerDoesNotResolveTheNextOwnersList() =
        runTest {
            val ownerContext = FakeOwnerContext(LOCAL_OWNER)
            val repository = OwnerScopedVehicleRepository(ownerContext)
            val holder = ownerScopedListHolder(ownerContext, repository)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { holder.state.collect() }
            advanceUntilIdle()

            repository.resultsFor(LOCAL_OWNER).emit(Outcome.Ok(emptyList()))
            advanceUntilIdle()
            assertFalse(holder.state.value.isLoading)

            ownerContext.owners.value = SIGNED_IN_OWNER
            advanceUntilIdle()

            assertTrue(
                holder.state.value.isLoading,
                "A new owner's list is unknown until that owner publishes a successful result.",
            )

            repository.resultsFor(SIGNED_IN_OWNER).emit(Outcome.Ok(listOf(vehicle())))
            advanceUntilIdle()

            assertFalse(holder.state.value.isLoading)
            assertEquals(1, holder.state.value.vehicles.size)
            holder.close()
        }

    @Test
    fun anInitialReadFailureLeavesTheListUnknownAndPublishesTheError() =
        runTest {
            val ownerContext = FakeOwnerContext(LOCAL_OWNER)
            val repository = OwnerScopedVehicleRepository(ownerContext)
            val holder = ownerScopedListHolder(ownerContext, repository)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { holder.state.collect() }
            advanceUntilIdle()

            repository.resultsFor(LOCAL_OWNER).emit(Outcome.Err(PersistenceError.DatabaseUnavailable))
            advanceUntilIdle()

            assertTrue(
                holder.state.value.isLoading,
                "An unreadable list is not a confirmed empty list.",
            )
            assertEquals(
                PersistenceError.DatabaseUnavailable.code,
                holder.state.value.message
                    ?.code,
            )
            assertEquals(emptyList(), holder.state.value.vehicles)
            holder.close()
        }

    @Test
    fun aSuccessfulResultAfterAReadFailureResolvesTheList() =
        runTest {
            val ownerContext = FakeOwnerContext(LOCAL_OWNER)
            val repository = OwnerScopedVehicleRepository(ownerContext)
            val holder = ownerScopedListHolder(ownerContext, repository)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { holder.state.collect() }
            repository.resultsFor(LOCAL_OWNER).emit(Outcome.Err(PersistenceError.DatabaseUnavailable))
            advanceUntilIdle()

            repository.resultsFor(LOCAL_OWNER).emit(Outcome.Ok(emptyList()))
            advanceUntilIdle()

            assertFalse(holder.state.value.isLoading)
            holder.close()
        }

    private fun TestScope.ownerScopedListHolder(
        ownerContext: OwnerContext,
        repository: VehicleRepository,
    ): VehicleListStateHolder =
        VehicleListStateHolder(
            scope = backgroundScope,
            repository = repository,
            dispatchers = TestDispatcherProvider(),
            refreshVehicles = { Outcome.Ok(Unit) },
            ownerContext = ownerContext,
        )
}

private class FakeOwnerContext(
    initial: OwnerId,
) : OwnerContext {
    val owners = MutableStateFlow(initial)

    override val current: OwnerId get() = owners.value

    override fun observe(): Flow<OwnerId> = owners
}

/**
 * Mirrors `SqlDelightVehicleRepository`, which resolves the owner when the observation is
 * subscribed, so an emission always belongs to the owner that was current at subscription time.
 */
private class OwnerScopedVehicleRepository(
    private val ownerContext: FakeOwnerContext,
) : VehicleRepository {
    private val results = mutableMapOf<OwnerId, MutableSharedFlow<Outcome<List<Vehicle>, AppError>>>()

    fun resultsFor(owner: OwnerId): MutableSharedFlow<Outcome<List<Vehicle>, AppError>> =
        results.getOrPut(owner) { MutableSharedFlow(replay = 1) }

    override fun observeVehicles(includeDeleted: Boolean): Flow<Outcome<List<Vehicle>, AppError>> =
        flow { emitAll(resultsFor(ownerContext.current)) }

    override fun observeVehicle(id: EntityId): Flow<Outcome<Vehicle?, AppError>> = MutableStateFlow(Outcome.Ok(null))

    override fun observeVehicleEditFacts(id: EntityId): Flow<Outcome<VehicleEditFacts?, AppError>> =
        MutableStateFlow(Outcome.Ok(null))

    override suspend fun createVehicle(command: CreateVehicleCommand): Outcome<EntityId, AppError> =
        Outcome.Ok(EntityId(VEHICLE_ID))

    override suspend fun updateVehicle(command: UpdateVehicleCommand): Outcome<Unit, AppError> = Outcome.Ok(Unit)

    override suspend fun deleteVehicle(id: EntityId): Outcome<Unit, AppError> = Outcome.Ok(Unit)
}

private val SIGNED_IN_OWNER = OwnerId("signed-in-owner")

private class FakeVehicleRepository(
    private val observedVehicles: Flow<Outcome<List<Vehicle>, AppError>>? = null,
) : VehicleRepository {
    val vehicles = MutableStateFlow<Outcome<List<Vehicle>, AppError>>(Outcome.Ok(emptyList()))
    val editFacts = MutableStateFlow<Outcome<VehicleEditFacts?, AppError>>(Outcome.Ok(null))
    val includeDeletedArguments = mutableListOf<Boolean>()

    override fun observeVehicles(includeDeleted: Boolean): Flow<Outcome<List<Vehicle>, AppError>> {
        includeDeletedArguments += includeDeleted
        return observedVehicles ?: vehicles
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
private const val SECOND_VEHICLE_ID = "00000000-0000-4000-8000-000000000003"
private const val DELETED_VEHICLE_ID = "00000000-0000-4000-8000-000000000002"
