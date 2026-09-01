@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package com.ruizurraca.carapp.feature.fuel.domain

import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.model.ConsumptionReport
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.FuelEntry
import com.ruizurraca.carapp.core.model.FuelEntryListItem
import kotlinx.coroutines.flow.Flow
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
interface FuelEntryRepository {
    fun observeFuelEntries(
        vehicleId: EntityId,
        includeDeleted: Boolean,
    ): Flow<Outcome<List<FuelEntryListItem>, AppError>>

    suspend fun getFuelEntry(id: EntityId): Outcome<FuelEntry?, AppError>

    suspend fun createFuelEntry(command: CreateFuelEntryCommand): Outcome<EntityId, AppError>

    suspend fun updateFuelEntry(command: UpdateFuelEntryCommand): Outcome<Unit, AppError>

    suspend fun deleteFuelEntry(id: EntityId): Outcome<Unit, AppError>

    fun observeConsumption(vehicleId: EntityId): Flow<Outcome<ConsumptionReport, AppError>>
}
