package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.Vehicle
import com.ruizurraca.carapp.feature.vehicle.domain.CreateVehicleCommand
import com.ruizurraca.carapp.feature.vehicle.domain.UpdateVehicleCommand
import com.ruizurraca.carapp.feature.vehicle.domain.VehicleEditFacts
import com.ruizurraca.carapp.feature.vehicle.domain.VehicleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * Holds every Vehicle read and write until local owner adoption has run for the current owner.
 *
 * Without this gate an observation started by an owner transition can publish an empty list for the
 * new UID while the rows are still owned by the `LOCAL_OWNER` sentinel. A host reading that list
 * sees a confirmed empty list (`D-116`) and opens mandatory first-run creation (`D-121`) over data
 * that is one transaction away from arriving. The gate is a no-op for the sentinel itself and for
 * any owner with nothing waiting, so it costs one indexed count on the paths that do not need it.
 *
 * An adoption failure is a typed error on every path, never a silent wait and never a cancellation
 * of the caller (`D-125`). On a read it becomes an unreadable list, which `VehicleListUiState`
 * already models as "not known yet" with its own retry; on a write it becomes the write's error.
 */
internal class AdoptionGatedVehicleRepository(
    private val delegate: VehicleRepository,
    private val adoption: LocalOwnerAdoption,
) : VehicleRepository {
    override fun observeVehicles(includeDeleted: Boolean): Flow<Outcome<List<Vehicle>, AppError>> =
        gated { delegate.observeVehicles(includeDeleted) }

    override fun observeVehicle(id: EntityId): Flow<Outcome<Vehicle?, AppError>> = gated { delegate.observeVehicle(id) }

    override fun observeVehicleEditFacts(id: EntityId): Flow<Outcome<VehicleEditFacts?, AppError>> =
        gated { delegate.observeVehicleEditFacts(id) }

    override suspend fun createVehicle(command: CreateVehicleCommand): Outcome<EntityId, AppError> =
        gatedWrite { delegate.createVehicle(command) }

    override suspend fun updateVehicle(command: UpdateVehicleCommand): Outcome<Unit, AppError> =
        gatedWrite { delegate.updateVehicle(command) }

    override suspend fun deleteVehicle(id: EntityId): Outcome<Unit, AppError> =
        gatedWrite { delegate.deleteVehicle(id) }

    private fun <T> gated(upstream: () -> Flow<Outcome<T, AppError>>): Flow<Outcome<T, AppError>> =
        flow {
            when (val gate = adoption.awaitAdoption()) {
                is Outcome.Err -> emit(Outcome.Err(gate.error))
                is Outcome.Ok -> emitAll(upstream())
            }
        }

    private suspend fun <T> gatedWrite(write: suspend () -> Outcome<T, AppError>): Outcome<T, AppError> =
        when (val gate = adoption.awaitAdoption()) {
            is Outcome.Err -> {
                Outcome.Err(gate.error)
            }

            is Outcome.Ok -> {
                val result = write()
                // A write that lands under the sentinel is the moment a device that started locally
                // first gains something to adopt, and it may be the only trigger it ever gets if it
                // was already online when it started (`D-124`). The guards inside decide.
                if (result is Outcome.Ok) adoption.onLocalOwnerWriteCommitted()
                result
            }
        }
}
