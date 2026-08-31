package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.common.AuthProvider
import com.ruizurraca.carapp.core.common.Confirmation
import com.ruizurraca.carapp.core.common.SyncStatus
import com.ruizurraca.carapp.core.common.UiMessage
import com.ruizurraca.carapp.core.model.ConsumptionInvalidReason

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

enum class MoneyInputMode { LITERS_AND_PRICE, LITERS_AND_TOTAL, PRICE_AND_TOTAL }

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

enum class SessionPhase { UNKNOWN, LOCAL, ANONYMOUS, PERMANENT, SIGNED_OUT, DELETING }

data class SessionUiState(
    val phase: SessionPhase,
    val providers: List<AuthProvider>,
    val isBusy: Boolean,
    val message: UiMessage?,
)

data class SyncUiState(
    val status: SyncStatus,
    val isOnline: Boolean,
    val message: UiMessage?,
)
