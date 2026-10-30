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

/**
 * The graph boundary of the vehicle holders for `docs/CONTRACTS.md §20.3.1` (`D-243`).
 *
 * `docs/TECHNICAL_PLAN.md §4` keeps feature `presentation` away from `:core:crash`, so the vehicle
 * holders cannot apply the non-fatal policy where they turn an `AppError` into a `UiMessage`. Every
 * error they convert, except the `SyncController` result of a refresh, comes out of this repository,
 * so this outermost decorator offers each one to [NonFatalReporting] on its way out. Results are
 * returned unchanged.
 */
internal class NonFatalReportingVehicleRepository(
    private val delegate: VehicleRepository,
    internal val nonFatals: NonFatalReporting,
) : VehicleRepository {
    override fun observeVehicles(includeDeleted: Boolean): Flow<Outcome<List<Vehicle>, AppError>> =
        delegate.observeVehicles(includeDeleted).reportingTo(nonFatals)

    override fun observeVehicle(id: EntityId): Flow<Outcome<Vehicle?, AppError>> =
        delegate.observeVehicle(id).reportingTo(nonFatals)

    override fun observeVehicleEditFacts(id: EntityId): Flow<Outcome<VehicleEditFacts?, AppError>> =
        delegate.observeVehicleEditFacts(id).reportingTo(nonFatals)

    override suspend fun createVehicle(command: CreateVehicleCommand): Outcome<EntityId, AppError> =
        delegate.createVehicle(command).alsoReportTo(nonFatals)

    override suspend fun updateVehicle(command: UpdateVehicleCommand): Outcome<Unit, AppError> =
        delegate.updateVehicle(command).alsoReportTo(nonFatals)

    override suspend fun deleteVehicle(id: EntityId): Outcome<Unit, AppError> =
        delegate.deleteVehicle(id).alsoReportTo(nonFatals)
}
