package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.SyncTrigger
import com.ruizurraca.carapp.core.model.ConsumptionReport
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.FuelEntry
import com.ruizurraca.carapp.core.model.FuelEntryListItem
import com.ruizurraca.carapp.core.sync.SyncController
import com.ruizurraca.carapp.feature.fuel.domain.CreateFuelEntryCommand
import com.ruizurraca.carapp.feature.fuel.domain.FuelEntryRepository
import com.ruizurraca.carapp.feature.fuel.domain.UpdateFuelEntryCommand
import kotlinx.coroutines.flow.Flow

/**
 * Fires the `docs/CONTRACTS.md §9.8` post-write trigger for every committed Fuel Entry write.
 *
 * Fuel entries are the data the owner produces most often, and before this decorator none of their
 * three write paths requested a cycle at all: the row sat in the outbox until a foreground return
 * past `FOREGROUND_RESUME_THRESHOLD_MS`, a connectivity recovery, a pull-to-refresh, or the
 * six-hour `Periodic` cadence happened to arrive.
 *
 * `requestSync` is used, never `sync`: a local write MUST NOT block on a network round trip
 * (`§20.7`).
 */
internal class SyncRequestingFuelEntryRepository(
    private val delegate: FuelEntryRepository,
    private val syncController: SyncController,
) : FuelEntryRepository {
    override fun observeFuelEntries(
        vehicleId: EntityId,
        includeDeleted: Boolean,
    ): Flow<Outcome<List<FuelEntryListItem>, AppError>> = delegate.observeFuelEntries(vehicleId, includeDeleted)

    override suspend fun getFuelEntry(id: EntityId): Outcome<FuelEntry?, AppError> = delegate.getFuelEntry(id)

    override suspend fun createFuelEntry(command: CreateFuelEntryCommand): Outcome<EntityId, AppError> =
        delegate.createFuelEntry(command).alsoRequestPostWriteSync()

    override suspend fun updateFuelEntry(command: UpdateFuelEntryCommand): Outcome<Unit, AppError> =
        delegate.updateFuelEntry(command).alsoRequestPostWriteSync()

    override suspend fun deleteFuelEntry(id: EntityId): Outcome<Unit, AppError> =
        delegate.deleteFuelEntry(id).alsoRequestPostWriteSync()

    override fun observeConsumption(vehicleId: EntityId): Flow<Outcome<ConsumptionReport, AppError>> =
        delegate.observeConsumption(vehicleId)

    private fun <T> Outcome<T, AppError>.alsoRequestPostWriteSync(): Outcome<T, AppError> =
        also {
            // A rejected write left no outbox row, so there is nothing to back up and nothing to
            // trigger.
            if (it is Outcome.Ok) syncController.requestSync(SyncTrigger.PostWriteDebounce)
        }
}
