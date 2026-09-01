@file:OptIn(kotlin.experimental.ExperimentalObjCName::class)

package com.ruizurraca.carapp.feature.fuel.presentation

import com.ruizurraca.carapp.core.common.SyncStatus
import com.ruizurraca.carapp.core.common.UiMessage
import com.ruizurraca.carapp.core.model.ConsumptionInvalidReason
import kotlin.native.ObjCName

@ObjCName(name = "SharedFuelEntryListUiState", swiftName = "FuelEntryListUiState", exact = true)
data class FuelEntryListUiState(
    val vehicleId: String,
    val isLoading: Boolean,
    val entries: List<FuelEntryListItemUi>,
    val consumptionAverageScaled: Long?,
    val validConsumptionSegmentCount: Int,
    val isConsumptionReliable: Boolean,
    val syncStatus: SyncStatus,
    val message: UiMessage?,
)

@ObjCName(name = "SharedFuelEntryListItemUi", swiftName = "FuelEntryListItemUi", exact = true)
data class FuelEntryListItemUi(
    val id: String,
    val dateEpochMillis: Long,
    val odometerKm: Long,
    val litersScaled: Long,
    val totalCostMinor: Long,
    val currencyCode: String,
    val isFullTank: Boolean,
    val consumptionScaled: Long?,
    val invalidReason: ConsumptionInvalidReason?,
    val hasMissedEntries: Boolean,
    val odometerInconsistent: Boolean,
)

@ObjCName(name = "SharedMoneyInputMode", swiftName = "MoneyInputMode", exact = true)
enum class MoneyInputMode { LITERS_AND_PRICE, LITERS_AND_TOTAL, PRICE_AND_TOTAL }

@ObjCName(name = "SharedFuelEntryFormUiState", swiftName = "FuelEntryFormUiState", exact = true)
data class FuelEntryFormUiState(
    val vehicleId: String,
    val entryId: String?,
    val dateEpochMillis: Long,
    val odometerKm: Long,
    val moneyInputMode: MoneyInputMode,
    val litersScaled: Long?,
    val pricePerLiterScaled: Long?,
    val totalCostMinor: Long?,
    val currencyCode: String,
    val isFullTank: Boolean,
    val hasMissedEntries: Boolean,
    val notes: String?,
    val isSaving: Boolean,
    val message: UiMessage?,
)
