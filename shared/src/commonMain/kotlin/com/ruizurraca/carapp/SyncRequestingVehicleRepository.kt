package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.SyncTrigger
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.Vehicle
import com.ruizurraca.carapp.core.sync.SyncController
import com.ruizurraca.carapp.feature.vehicle.domain.CreateVehicleCommand
import com.ruizurraca.carapp.feature.vehicle.domain.UpdateVehicleCommand
import com.ruizurraca.carapp.feature.vehicle.domain.VehicleEditFacts
import com.ruizurraca.carapp.feature.vehicle.domain.VehicleRepository
import kotlinx.coroutines.flow.Flow

/**
 * Fires the `docs/CONTRACTS.md §9.8` post-write trigger for every committed Vehicle write.
 *
 * The trigger belongs here rather than at the call site because the outbox row belongs here: every
 * write that commits under a real owner leaves one, and a row with no trigger behind it waits for an
 * unrelated later trigger - the six-hour `Periodic` cadence in the worst case. Wrapping the whole
 * repository is what makes the rule exhaustive, since a delete reaches the repository directly from
 * `VehicleListStateHolder` and never passes through a runtime wrapper.
 *
 * `requestSync` is used, never `sync`: a local write MUST NOT block on a network round trip
 * (`§20.7`). The 2 s window of `D-186` coalesces a burst of writes into one cycle, so requesting per
 * write costs no extra cycle.
 */
internal class SyncRequestingVehicleRepository(
    private val delegate: VehicleRepository,
    private val syncController: SyncController,
) : VehicleRepository {
    override fun observeVehicles(includeDeleted: Boolean): Flow<Outcome<List<Vehicle>, AppError>> =
        delegate.observeVehicles(includeDeleted)

    override fun observeVehicle(id: EntityId): Flow<Outcome<Vehicle?, AppError>> = delegate.observeVehicle(id)

    override fun observeVehicleEditFacts(id: EntityId): Flow<Outcome<VehicleEditFacts?, AppError>> =
        delegate.observeVehicleEditFacts(id)

    override suspend fun createVehicle(command: CreateVehicleCommand): Outcome<EntityId, AppError> =
        delegate.createVehicle(command).alsoRequestPostWriteSync()

    override suspend fun updateVehicle(command: UpdateVehicleCommand): Outcome<Unit, AppError> =
        delegate.updateVehicle(command).alsoRequestPostWriteSync()

    override suspend fun deleteVehicle(id: EntityId): Outcome<Unit, AppError> =
        delegate.deleteVehicle(id).alsoRequestPostWriteSync()

    private fun <T> Outcome<T, AppError>.alsoRequestPostWriteSync(): Outcome<T, AppError> =
        also {
            // A rejected write left no outbox row, so there is nothing to back up and nothing to
            // trigger.
            if (it is Outcome.Ok) syncController.requestSync(SyncTrigger.PostWriteDebounce)
        }
}
