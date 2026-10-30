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
 * The graph boundary of the fuel-entry holders for `docs/CONTRACTS.md §20.3.1` (`D-243`), for the
 * same reason as [NonFatalReportingVehicleRepository]: every error those holders turn into a
 * `UiMessage` comes out of this repository, except the validation errors they build locally, which
 * are never `UnexpectedError`.
 */
internal class NonFatalReportingFuelEntryRepository(
    private val delegate: FuelEntryRepository,
    internal val nonFatals: NonFatalReporting,
) : FuelEntryRepository {
    override fun observeFuelEntries(
        vehicleId: EntityId,
        includeDeleted: Boolean,
    ): Flow<Outcome<List<FuelEntryListItem>, AppError>> =
        delegate.observeFuelEntries(vehicleId, includeDeleted).reportingTo(nonFatals)

    override suspend fun getFuelEntry(id: EntityId): Outcome<FuelEntry?, AppError> =
        delegate.getFuelEntry(id).alsoReportTo(nonFatals)

    override suspend fun createFuelEntry(command: CreateFuelEntryCommand): Outcome<EntityId, AppError> =
        delegate.createFuelEntry(command).alsoReportTo(nonFatals)

    override suspend fun updateFuelEntry(command: UpdateFuelEntryCommand): Outcome<Unit, AppError> =
        delegate.updateFuelEntry(command).alsoReportTo(nonFatals)

    override suspend fun deleteFuelEntry(id: EntityId): Outcome<Unit, AppError> =
        delegate.deleteFuelEntry(id).alsoReportTo(nonFatals)

    override fun observeConsumption(vehicleId: EntityId): Flow<Outcome<ConsumptionReport, AppError>> =
        delegate.observeConsumption(vehicleId).reportingTo(nonFatals)
}
