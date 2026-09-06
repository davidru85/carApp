package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.model.ConsumptionReport
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.FuelEntry
import com.ruizurraca.carapp.core.model.FuelEntryListItem
import com.ruizurraca.carapp.feature.fuel.domain.CreateFuelEntryCommand
import com.ruizurraca.carapp.feature.fuel.domain.FuelEntryRepository
import com.ruizurraca.carapp.feature.fuel.domain.UpdateFuelEntryCommand
import kotlinx.coroutines.flow.Flow

/**
 * Applies the `docs/CONTRACTS.md §11.2` post-commit re-evaluation to Fuel Entry writes.
 *
 * Every synchronized write that commits under the sentinel is a moment the device may have gained
 * something to adopt, and for a device that was already online when it started locally it is the
 * only trigger it will ever get. Vehicles are not the whole story: an owner who created a vehicle
 * before the network came back can go on adding fuel entries for hours without touching it.
 *
 * Reads are deliberately not gated. `D-125` gates the Vehicle side, and a fuel-entry read is only
 * reachable through a resolved vehicle, so no fuel read can precede adoption. The attempt itself
 * stays asynchronous inside [LocalOwnerAdoption.onLocalOwnerWriteCommitted], so a local write never
 * waits for a network round trip.
 */
internal class AdoptionNotifyingFuelEntryRepository(
    private val delegate: FuelEntryRepository,
    private val adoption: LocalOwnerAdoption,
) : FuelEntryRepository {
    override fun observeFuelEntries(
        vehicleId: EntityId,
        includeDeleted: Boolean,
    ): Flow<Outcome<List<FuelEntryListItem>, AppError>> = delegate.observeFuelEntries(vehicleId, includeDeleted)

    override suspend fun getFuelEntry(id: EntityId): Outcome<FuelEntry?, AppError> = delegate.getFuelEntry(id)

    override suspend fun createFuelEntry(command: CreateFuelEntryCommand): Outcome<EntityId, AppError> =
        delegate.createFuelEntry(command).alsoReevaluateAcquisition()

    override suspend fun updateFuelEntry(command: UpdateFuelEntryCommand): Outcome<Unit, AppError> =
        delegate.updateFuelEntry(command).alsoReevaluateAcquisition()

    override suspend fun deleteFuelEntry(id: EntityId): Outcome<Unit, AppError> =
        delegate.deleteFuelEntry(id).alsoReevaluateAcquisition()

    override fun observeConsumption(vehicleId: EntityId): Flow<Outcome<ConsumptionReport, AppError>> =
        delegate.observeConsumption(vehicleId)

    private fun <T> Outcome<T, AppError>.alsoReevaluateAcquisition(): Outcome<T, AppError> =
        also {
            if (it is Outcome.Ok) adoption.onLocalOwnerWriteCommitted()
        }
}
