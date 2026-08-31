@file:OptIn(kotlin.experimental.ExperimentalObjCName::class)

package com.ruizurraca.carapp.feature.vehicle.presentation

import com.ruizurraca.carapp.core.common.SyncStatus
import com.ruizurraca.carapp.core.common.UiMessage
import com.ruizurraca.carapp.core.model.FuelType
import kotlin.native.ObjCName

@ObjCName(name = "SharedVehicleListUiState", swiftName = "VehicleListUiState", exact = true)
data class VehicleListUiState(
    val isLoading: Boolean,
    val vehicles: List<VehicleListItemUi>,
    val selectedVehicleId: String?,
    val syncStatus: SyncStatus,
    val message: UiMessage?,
)

@ObjCName(name = "SharedVehicleListItemUi", swiftName = "VehicleListItemUi", exact = true)
data class VehicleListItemUi(
    val id: String,
    val name: String,
    val currentOdometerKm: Long,
    val fuelType: FuelType,
    val deleted: Boolean,
)

@ObjCName(name = "SharedVehicleFormUiState", swiftName = "VehicleFormUiState", exact = true)
data class VehicleFormUiState(
    val vehicleId: String?,
    val savedVehicleId: String?,
    val name: String,
    val initialOdometerKm: Long,
    val brand: String?,
    val model: String?,
    val fuelType: FuelType,
    val canEditInitialOdometer: Boolean,
    val isSaving: Boolean,
    val message: UiMessage?,
)
