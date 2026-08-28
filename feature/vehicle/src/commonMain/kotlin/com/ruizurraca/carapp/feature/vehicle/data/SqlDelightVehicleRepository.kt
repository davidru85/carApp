package com.ruizurraca.carapp.feature.vehicle.data

import com.ruizurraca.carapp.core.common.AppClock
import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.OwnerContext
import com.ruizurraca.carapp.core.common.UnexpectedError
import com.ruizurraca.carapp.core.common.UuidGenerator
import com.ruizurraca.carapp.core.database.AppDatabase
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.Vehicle
import com.ruizurraca.carapp.feature.vehicle.domain.CreateVehicleCommand
import com.ruizurraca.carapp.feature.vehicle.domain.UpdateVehicleCommand
import com.ruizurraca.carapp.feature.vehicle.domain.VehicleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class SqlDelightVehicleRepository internal constructor(
    private val localDataSource: VehicleLocalDataSource,
    private val ownerContext: OwnerContext,
    private val clock: AppClock,
    private val uuidGenerator: UuidGenerator,
) : VehicleRepository {
    constructor(
        database: AppDatabase,
        ownerContext: OwnerContext,
        clock: AppClock,
        uuidGenerator: UuidGenerator,
    ) : this(
        localDataSource = SqlDelightVehicleLocalDataSource(database),
        ownerContext = ownerContext,
        clock = clock,
        uuidGenerator = uuidGenerator,
    )

    override fun observeVehicles(includeDeleted: Boolean): Flow<Outcome<List<Vehicle>, AppError>> =
        flowOf(Outcome.Ok(emptyList()))

    override fun observeVehicle(id: EntityId): Flow<Outcome<Vehicle?, AppError>> = flowOf(Outcome.Ok(null))

    override suspend fun createVehicle(command: CreateVehicleCommand): Outcome<EntityId, AppError> =
        Outcome.Err(UnexpectedError(":feature:vehicle", "RedPhase"))

    override suspend fun updateVehicle(command: UpdateVehicleCommand): Outcome<Unit, AppError> =
        Outcome.Err(UnexpectedError(":feature:vehicle", "RedPhase"))

    override suspend fun deleteVehicle(id: EntityId): Outcome<Unit, AppError> =
        Outcome.Err(UnexpectedError(":feature:vehicle", "RedPhase"))
}
