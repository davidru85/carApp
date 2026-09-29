package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.analytics.AnalyticsEvent
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
 * Emits the `docs/CONTRACTS.md §16.1` vehicle-write event and refreshes the user properties.
 *
 * A create is the one vehicle write the closed hierarchy names: there is no `VehicleUpdated` or
 * `VehicleDeleted` leaf, and adding one is a contract change. A delete therefore refreshes the
 * buckets and emits nothing, which is exactly what the cadence rule asks for.
 *
 * Only an `Outcome.Ok` is reported. A rejected or failed write mutated nothing, so it is not a
 * product event — the failure leaves of the hierarchy describe auth, conversion, deletion and sync
 * outcomes, not a validation rejection on a form.
 *
 * Reads are passed through untouched: `§16.1` forbids analytics in data logic, and a read is data
 * logic whatever it returns.
 */
internal class AnalyticsNotifyingVehicleRepository(
    private val delegate: VehicleRepository,
    private val emissions: AnalyticsEmissions,
) : VehicleRepository {
    override fun observeVehicles(includeDeleted: Boolean): Flow<Outcome<List<Vehicle>, AppError>> =
        delegate.observeVehicles(includeDeleted)

    override fun observeVehicle(id: EntityId): Flow<Outcome<Vehicle?, AppError>> = delegate.observeVehicle(id)

    override fun observeVehicleEditFacts(id: EntityId): Flow<Outcome<VehicleEditFacts?, AppError>> =
        delegate.observeVehicleEditFacts(id)

    override suspend fun createVehicle(command: CreateVehicleCommand): Outcome<EntityId, AppError> =
        delegate.createVehicle(command).alsoNotify(AnalyticsEvent.VehicleCreated)

    override suspend fun updateVehicle(command: UpdateVehicleCommand): Outcome<Unit, AppError> =
        delegate.updateVehicle(command).alsoRefreshBuckets()

    override suspend fun deleteVehicle(id: EntityId): Outcome<Unit, AppError> =
        delegate.deleteVehicle(id).alsoRefreshBuckets()

    private suspend fun <T> Outcome<T, AppError>.alsoNotify(event: AnalyticsEvent): Outcome<T, AppError> =
        also {
            if (it is Outcome.Ok) {
                emissions.trackAndRefresh(event)
            }
        }

    private suspend fun <T> Outcome<T, AppError>.alsoRefreshBuckets(): Outcome<T, AppError> =
        also {
            if (it is Outcome.Ok) emissions.refreshUserProperties()
        }
}
