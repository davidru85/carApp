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
import kotlinx.coroutines.flow.onStart

/**
 * Holds every Vehicle read and write until local owner adoption has run for the current owner.
 *
 * Without this gate an observation started by an owner transition can publish an empty list for the
 * new UID while the rows are still owned by the `LOCAL_OWNER` sentinel. A host reading that list
 * sees a confirmed empty list (`D-116`) and opens mandatory first-run creation (`D-121`) over data
 * that is one transaction away from arriving. The gate is a no-op for the sentinel itself and for
 * any owner with nothing waiting, so it costs one indexed count on the paths that do not need it.
 */
internal class AdoptionGatedVehicleRepository(
    private val delegate: VehicleRepository,
    private val adoption: LocalOwnerAdoption,
) : VehicleRepository {
    override fun observeVehicles(includeDeleted: Boolean): Flow<Outcome<List<Vehicle>, AppError>> =
        delegate.observeVehicles(includeDeleted).gated()

    override fun observeVehicle(id: EntityId): Flow<Outcome<Vehicle?, AppError>> = delegate.observeVehicle(id).gated()

    override fun observeVehicleEditFacts(id: EntityId): Flow<Outcome<VehicleEditFacts?, AppError>> =
        delegate.observeVehicleEditFacts(id).gated()

    override suspend fun createVehicle(command: CreateVehicleCommand): Outcome<EntityId, AppError> {
        adoption.awaitAdoption()
        return delegate.createVehicle(command)
    }

    override suspend fun updateVehicle(command: UpdateVehicleCommand): Outcome<Unit, AppError> {
        adoption.awaitAdoption()
        return delegate.updateVehicle(command)
    }

    override suspend fun deleteVehicle(id: EntityId): Outcome<Unit, AppError> {
        adoption.awaitAdoption()
        return delegate.deleteVehicle(id)
    }

    private fun <T> Flow<T>.gated(): Flow<T> = onStart { adoption.awaitAdoption() }
}
