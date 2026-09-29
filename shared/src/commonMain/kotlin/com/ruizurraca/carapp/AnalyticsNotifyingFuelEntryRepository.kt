package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.analytics.AnalyticsEvent
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
 * Emits the `docs/CONTRACTS.md §16.1` fuel-entry-write event and refreshes the user properties.
 *
 * `FuelEntryCreated` carries `isFullTank` and `hadNotes` — bucket-level booleans only. `hadNotes` is
 * derived from the command, which is where the note text lives, so the text itself never leaves this
 * class: the leaf has nowhere to put it.
 *
 * A delete emits no event, because the closed hierarchy declares no `FuelEntryDeleted` leaf; it does
 * refresh the buckets, which is what the cadence rule asks for. Only `Outcome.Ok` is reported: a
 * rejected write mutated nothing.
 */
internal class AnalyticsNotifyingFuelEntryRepository(
    private val delegate: FuelEntryRepository,
    private val emissions: AnalyticsEmissions,
) : FuelEntryRepository {
    override fun observeFuelEntries(
        vehicleId: EntityId,
        includeDeleted: Boolean,
    ): Flow<Outcome<List<FuelEntryListItem>, AppError>> = delegate.observeFuelEntries(vehicleId, includeDeleted)

    override suspend fun getFuelEntry(id: EntityId): Outcome<FuelEntry?, AppError> = delegate.getFuelEntry(id)

    override suspend fun createFuelEntry(command: CreateFuelEntryCommand): Outcome<EntityId, AppError> =
        delegate.createFuelEntry(command).also {
            if (it is Outcome.Ok) {
                emissions.trackAndRefresh(
                    AnalyticsEvent.FuelEntryCreated(
                        isFullTank = command.isFullTank,
                        hadNotes = !command.notes.isNullOrBlank(),
                    ),
                )
            }
        }

    override suspend fun updateFuelEntry(command: UpdateFuelEntryCommand): Outcome<Unit, AppError> =
        delegate.updateFuelEntry(command).alsoRefreshBuckets()

    override suspend fun deleteFuelEntry(id: EntityId): Outcome<Unit, AppError> =
        delegate.deleteFuelEntry(id).alsoRefreshBuckets()

    override fun observeConsumption(vehicleId: EntityId): Flow<Outcome<ConsumptionReport, AppError>> =
        delegate.observeConsumption(vehicleId)

    private suspend fun <T> Outcome<T, AppError>.alsoRefreshBuckets(): Outcome<T, AppError> =
        also {
            if (it is Outcome.Ok) emissions.refreshUserProperties()
        }
}
