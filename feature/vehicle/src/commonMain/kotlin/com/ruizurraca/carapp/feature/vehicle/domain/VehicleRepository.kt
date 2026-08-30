package com.ruizurraca.carapp.feature.vehicle.domain

import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.Vehicle
import kotlinx.coroutines.flow.Flow

interface VehicleRepository {
    fun observeVehicles(includeDeleted: Boolean): Flow<Outcome<List<Vehicle>, AppError>>

    fun observeVehicle(id: EntityId): Flow<Outcome<Vehicle?, AppError>>

    fun observeVehicleEditFacts(id: EntityId): Flow<Outcome<VehicleEditFacts?, AppError>>

    suspend fun createVehicle(command: CreateVehicleCommand): Outcome<EntityId, AppError>

    suspend fun updateVehicle(command: UpdateVehicleCommand): Outcome<Unit, AppError>

    suspend fun deleteVehicle(id: EntityId): Outcome<Unit, AppError>
}

data class VehicleEditFacts(
    val vehicle: Vehicle,
    val canEditInitialOdometer: Boolean,
)
