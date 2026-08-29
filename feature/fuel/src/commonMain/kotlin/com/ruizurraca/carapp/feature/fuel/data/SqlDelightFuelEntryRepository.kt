package com.ruizurraca.carapp.feature.fuel.data

import com.ruizurraca.carapp.core.common.AppClock
import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.OwnerContext
import com.ruizurraca.carapp.core.common.UnexpectedError
import com.ruizurraca.carapp.core.common.UuidGenerator
import com.ruizurraca.carapp.core.database.FuelEntryDatabaseAccess
import com.ruizurraca.carapp.core.model.ConsumptionReport
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.FuelEntry
import com.ruizurraca.carapp.core.model.FuelEntryListItem
import com.ruizurraca.carapp.feature.fuel.domain.CalculateConsumption
import com.ruizurraca.carapp.feature.fuel.domain.CreateFuelEntryCommand
import com.ruizurraca.carapp.feature.fuel.domain.DefaultCalculateConsumption
import com.ruizurraca.carapp.feature.fuel.domain.FuelEntryRepository
import com.ruizurraca.carapp.feature.fuel.domain.UpdateFuelEntryCommand
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class SqlDelightFuelEntryRepository internal constructor(
    private val localDataSource: FuelEntryLocalDataSource,
    private val ownerContext: OwnerContext,
    private val clock: AppClock,
    private val uuidGenerator: UuidGenerator,
    private val calculateConsumption: CalculateConsumption,
) : FuelEntryRepository {
    constructor(
        databaseAccess: FuelEntryDatabaseAccess,
        ownerContext: OwnerContext,
        clock: AppClock,
        uuidGenerator: UuidGenerator,
    ) : this(
        localDataSource = SqlDelightFuelEntryLocalDataSource(databaseAccess),
        ownerContext = ownerContext,
        clock = clock,
        uuidGenerator = uuidGenerator,
        calculateConsumption = DefaultCalculateConsumption(),
    )

    override fun observeFuelEntries(
        vehicleId: EntityId,
        includeDeleted: Boolean,
    ): Flow<Outcome<List<FuelEntryListItem>, AppError>> = flowOf(Outcome.Ok(emptyList()))

    override suspend fun getFuelEntry(id: EntityId): Outcome<FuelEntry?, AppError> = Outcome.Ok(null)

    override suspend fun createFuelEntry(command: CreateFuelEntryCommand): Outcome<EntityId, AppError> =
        Outcome.Err(UnexpectedError(":feature:fuel", "RedPhase"))

    override suspend fun updateFuelEntry(command: UpdateFuelEntryCommand): Outcome<Unit, AppError> =
        Outcome.Err(UnexpectedError(":feature:fuel", "RedPhase"))

    override suspend fun deleteFuelEntry(id: EntityId): Outcome<Unit, AppError> =
        Outcome.Err(UnexpectedError(":feature:fuel", "RedPhase"))

    override fun observeConsumption(vehicleId: EntityId): Flow<Outcome<ConsumptionReport, AppError>> =
        flowOf(
            Outcome.Ok(
                ConsumptionReport(
                    segments = emptyList(),
                    validSegmentCount = 0,
                    average = null,
                    isReliable = false,
                ),
            ),
        )
}
