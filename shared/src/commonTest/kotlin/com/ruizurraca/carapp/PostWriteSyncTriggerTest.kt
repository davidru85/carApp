package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.Confirmation
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.SyncStatus
import com.ruizurraca.carapp.core.common.SyncTrigger
import com.ruizurraca.carapp.core.model.ConsumptionReport
import com.ruizurraca.carapp.core.model.CurrencyCode
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.FuelEntry
import com.ruizurraca.carapp.core.model.FuelEntryListItem
import com.ruizurraca.carapp.core.model.FuelType
import com.ruizurraca.carapp.core.model.Vehicle
import com.ruizurraca.carapp.core.sync.SyncController
import com.ruizurraca.carapp.feature.fuel.domain.CreateFuelEntryCommand
import com.ruizurraca.carapp.feature.fuel.domain.FuelEntryRepository
import com.ruizurraca.carapp.feature.fuel.domain.MoneyInput
import com.ruizurraca.carapp.feature.fuel.domain.UpdateFuelEntryCommand
import com.ruizurraca.carapp.feature.vehicle.domain.CreateVehicleCommand
import com.ruizurraca.carapp.feature.vehicle.domain.UpdateVehicleCommand
import com.ruizurraca.carapp.feature.vehicle.domain.VehicleEditFacts
import com.ruizurraca.carapp.feature.vehicle.domain.VehicleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * The `docs/CONTRACTS.md §9.8` post-write trigger, for every synchronized write rather than only for
 * a Vehicle create or update.
 *
 * Every one of these six calls writes an outbox row. A row with no trigger behind it waits for an
 * unrelated later trigger - a foreground return past the threshold, a connectivity recovery, a
 * pull-to-refresh, or the six-hour `Periodic` cadence - so the write is not backed up for as long as
 * that takes. `§9.8` names the post-write debounce as a trigger in its own right, and the 2 s window
 * of `D-186` is what coalesces a burst of them into one cycle.
 */
class PostWriteSyncTriggerTest {
    @Test
    fun everyCommittedVehicleWriteRequestsThePostWriteTrigger() =
        runTest {
            val controller = RecordingPostWriteController()
            val repository = SyncRequestingVehicleRepository(AcceptingVehicleRepository(), controller)

            repository.createVehicle(createVehicleCommand())
            repository.updateVehicle(updateVehicleCommand())
            repository.deleteVehicle(EntityId(ENTITY))

            assertEquals(
                List(3) { SyncTrigger.PostWriteDebounce },
                controller.requested,
                "a Vehicle create, update and delete each write an outbox row, so each is a §9.8 post-write trigger",
            )
        }

    @Test
    fun everyCommittedFuelEntryWriteRequestsThePostWriteTrigger() =
        runTest {
            val controller = RecordingPostWriteController()
            val repository = SyncRequestingFuelEntryRepository(AcceptingFuelEntryRepository(), controller)

            repository.createFuelEntry(createFuelEntryCommand())
            repository.updateFuelEntry(updateFuelEntryCommand())
            repository.deleteFuelEntry(EntityId(ENTITY))

            assertEquals(
                List(3) { SyncTrigger.PostWriteDebounce },
                controller.requested,
                "a Fuel Entry create, update and delete each write an outbox row, so each is a §9.8 post-write trigger",
            )
        }

    @Test
    fun aRejectedWriteRequestsNothing() =
        runTest {
            val controller = RecordingPostWriteController()
            val vehicles = SyncRequestingVehicleRepository(RejectingVehicleRepository(), controller)
            val entries = SyncRequestingFuelEntryRepository(RejectingFuelEntryRepository(), controller)

            vehicles.createVehicle(createVehicleCommand())
            vehicles.updateVehicle(updateVehicleCommand())
            vehicles.deleteVehicle(EntityId(ENTITY))
            entries.createFuelEntry(createFuelEntryCommand())
            entries.updateFuelEntry(updateFuelEntryCommand())
            entries.deleteFuelEntry(EntityId(ENTITY))

            assertEquals(
                emptyList(),
                controller.requested,
                "a write that produced no outbox row has nothing to back up, so it is not a trigger",
            )
        }

    @Test
    fun readsAreDelegatedUntouched() =
        runTest {
            val controller = RecordingPostWriteController()
            val vehicles = SyncRequestingVehicleRepository(AcceptingVehicleRepository(), controller)
            val entries = SyncRequestingFuelEntryRepository(AcceptingFuelEntryRepository(), controller)

            vehicles.observeVehicles(includeDeleted = false)
            vehicles.observeVehicle(EntityId(ENTITY))
            vehicles.observeVehicleEditFacts(EntityId(ENTITY))
            entries.observeFuelEntries(EntityId(ENTITY), includeDeleted = false)
            entries.observeConsumption(EntityId(ENTITY))
            entries.getFuelEntry(EntityId(ENTITY))

            assertEquals(emptyList(), controller.requested, "a read is not a write and never triggers a cycle")
        }

    private fun createVehicleCommand() =
        CreateVehicleCommand(
            name = "Car",
            initialOdometerKm = 0L,
            brand = null,
            model = null,
            fuelType = FuelType.GASOLINE,
            confirmations = emptySet<Confirmation>(),
        )

    private fun updateVehicleCommand() =
        UpdateVehicleCommand(
            id = EntityId(ENTITY),
            name = "Car",
            initialOdometerKm = null,
            brand = null,
            model = null,
            fuelType = FuelType.GASOLINE,
            confirmations = emptySet<Confirmation>(),
        )

    private fun createFuelEntryCommand() =
        CreateFuelEntryCommand(
            vehicleId = EntityId(ENTITY),
            date = Instant.fromEpochMilliseconds(0),
            odometerKm = 10L,
            money = MoneyInput.LitersAndPrice(litersScaled = 1_000L, pricePerLiterScaled = 1_000L),
            currency = CurrencyCode("EUR"),
            isFullTank = true,
            hasMissedEntries = false,
            notes = null,
            confirmations = emptySet<Confirmation>(),
        )

    private fun updateFuelEntryCommand() =
        UpdateFuelEntryCommand(
            id = EntityId(ENTITY),
            vehicleId = EntityId(ENTITY),
            date = Instant.fromEpochMilliseconds(0),
            odometerKm = 10L,
            money = MoneyInput.LitersAndPrice(litersScaled = 1_000L, pricePerLiterScaled = 1_000L),
            currency = CurrencyCode("EUR"),
            isFullTank = true,
            hasMissedEntries = false,
            notes = null,
            confirmations = emptySet<Confirmation>(),
        )

    private companion object {
        const val ENTITY = "11111111-1111-4111-8111-111111111111"
    }
}

/** Records the triggers the decorator requests, which is the observable of the post-write rule. */
private class RecordingPostWriteController : SyncController {
    val requested = mutableListOf<SyncTrigger>()

    override val status: StateFlow<SyncStatus> = MutableStateFlow(SyncStatus.Idle)

    override fun requestSync(reason: SyncTrigger) {
        requested += reason
    }

    override suspend fun sync(reason: SyncTrigger): Outcome<Unit, AppError> = Outcome.Ok(Unit)

    override suspend fun retryFailed(): Outcome<Unit, AppError> = Outcome.Ok(Unit)

    override fun shutdown() = Unit
}

private class AcceptingVehicleRepository : VehicleRepository {
    override fun observeVehicles(includeDeleted: Boolean): Flow<Outcome<List<Vehicle>, AppError>> =
        flowOf(Outcome.Ok(emptyList()))

    override fun observeVehicle(id: EntityId): Flow<Outcome<Vehicle?, AppError>> = flowOf(Outcome.Ok(null))

    override fun observeVehicleEditFacts(id: EntityId): Flow<Outcome<VehicleEditFacts?, AppError>> =
        flowOf(Outcome.Ok(null))

    override suspend fun createVehicle(command: CreateVehicleCommand): Outcome<EntityId, AppError> =
        Outcome.Ok(EntityId("11111111-1111-4111-8111-111111111111"))

    override suspend fun updateVehicle(command: UpdateVehicleCommand): Outcome<Unit, AppError> = Outcome.Ok(Unit)

    override suspend fun deleteVehicle(id: EntityId): Outcome<Unit, AppError> = Outcome.Ok(Unit)
}

private class RejectingVehicleRepository : VehicleRepository {
    override fun observeVehicles(includeDeleted: Boolean): Flow<Outcome<List<Vehicle>, AppError>> =
        flowOf(Outcome.Err(REJECTION))

    override fun observeVehicle(id: EntityId): Flow<Outcome<Vehicle?, AppError>> = flowOf(Outcome.Err(REJECTION))

    override fun observeVehicleEditFacts(id: EntityId): Flow<Outcome<VehicleEditFacts?, AppError>> =
        flowOf(Outcome.Err(REJECTION))

    override suspend fun createVehicle(command: CreateVehicleCommand): Outcome<EntityId, AppError> =
        Outcome.Err(REJECTION)

    override suspend fun updateVehicle(command: UpdateVehicleCommand): Outcome<Unit, AppError> = Outcome.Err(REJECTION)

    override suspend fun deleteVehicle(id: EntityId): Outcome<Unit, AppError> = Outcome.Err(REJECTION)
}

private class AcceptingFuelEntryRepository : FuelEntryRepository {
    override fun observeFuelEntries(
        vehicleId: EntityId,
        includeDeleted: Boolean,
    ): Flow<Outcome<List<FuelEntryListItem>, AppError>> = flowOf(Outcome.Ok(emptyList()))

    override suspend fun getFuelEntry(id: EntityId): Outcome<FuelEntry?, AppError> = Outcome.Ok(null)

    override suspend fun createFuelEntry(command: CreateFuelEntryCommand): Outcome<EntityId, AppError> =
        Outcome.Ok(EntityId("11111111-1111-4111-8111-111111111111"))

    override suspend fun updateFuelEntry(command: UpdateFuelEntryCommand): Outcome<Unit, AppError> = Outcome.Ok(Unit)

    override suspend fun deleteFuelEntry(id: EntityId): Outcome<Unit, AppError> = Outcome.Ok(Unit)

    override fun observeConsumption(vehicleId: EntityId): Flow<Outcome<ConsumptionReport, AppError>> = flowOf()
}

private class RejectingFuelEntryRepository : FuelEntryRepository {
    override fun observeFuelEntries(
        vehicleId: EntityId,
        includeDeleted: Boolean,
    ): Flow<Outcome<List<FuelEntryListItem>, AppError>> = flowOf(Outcome.Err(REJECTION))

    override suspend fun getFuelEntry(id: EntityId): Outcome<FuelEntry?, AppError> = Outcome.Err(REJECTION)

    override suspend fun createFuelEntry(command: CreateFuelEntryCommand): Outcome<EntityId, AppError> =
        Outcome.Err(REJECTION)

    override suspend fun updateFuelEntry(command: UpdateFuelEntryCommand): Outcome<Unit, AppError> =
        Outcome.Err(REJECTION)

    override suspend fun deleteFuelEntry(id: EntityId): Outcome<Unit, AppError> = Outcome.Err(REJECTION)

    override fun observeConsumption(vehicleId: EntityId): Flow<Outcome<ConsumptionReport, AppError>> = flowOf()
}

private val REJECTION: AppError = com.ruizurraca.carapp.core.common.PersistenceError.DatabaseUnavailable
