package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.SyncTrigger
import com.ruizurraca.carapp.core.database.AppDatabase
import com.ruizurraca.carapp.core.database.VehicleDatabaseAccess
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.sync.SyncController
import com.ruizurraca.carapp.feature.vehicle.data.SqlDelightVehicleRepository
import com.ruizurraca.carapp.feature.vehicle.domain.CreateVehicleCommand
import com.ruizurraca.carapp.feature.vehicle.domain.UpdateVehicleCommand
import com.ruizurraca.carapp.feature.vehicle.domain.VehicleRepository

/**
 * E0-07's internal remote adapter for the minimal Vehicle slice (`D-55`). Local operations delegate
 * to E1-03's complete repository; E3-02 and E3-03 replace the staged remote orchestration.
 */
internal class VehicleSliceRuntime(
    private val dependencies: AppGraphDependencies,
    private val database: AppDatabase,
    adoption: LocalOwnerAdoption,
    private val syncController: SyncController,
) {
    val repository: VehicleRepository =
        AdoptionGatedVehicleRepository(
            delegate =
                SqlDelightVehicleRepository(
                    databaseAccess = VehicleDatabaseAccess(database),
                    ownerContext = dependencies.ownerContext,
                    clock = dependencies.clock,
                    uuidGenerator = dependencies.uuidGenerator,
                ),
            adoption = adoption,
        )

    suspend fun createVehicle(command: CreateVehicleCommand): Outcome<EntityId, AppError> {
        val result = repository.createVehicle(command)
        if (result is Outcome.Ok) syncController.requestSync(SyncTrigger.PostWriteDebounce)
        return result
    }

    suspend fun updateVehicle(command: UpdateVehicleCommand): Outcome<Unit, AppError> {
        val result = repository.updateVehicle(command)
        if (result is Outcome.Ok) syncController.requestSync(SyncTrigger.PostWriteDebounce)
        return result
    }

    suspend fun refresh(): Outcome<Unit, AppError> =
        // A user-initiated refresh awaits the cycle that serves it, so the pull-to-refresh
        // indicator stays on until the outcome is known and a failed pull surfaces its error.
        syncController.sync(SyncTrigger.PullToRefresh)
}
