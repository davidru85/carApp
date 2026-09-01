@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package com.ruizurraca.carapp.feature.fuel.domain

import com.ruizurraca.carapp.core.common.Confirmation
import com.ruizurraca.carapp.core.model.CurrencyCode
import com.ruizurraca.carapp.core.model.EntityId
import kotlin.native.HiddenFromObjC
import kotlin.time.Instant

@HiddenFromObjC
sealed interface MoneyInput {
    @HiddenFromObjC
    data class LitersAndPrice(
        val litersScaled: Long,
        val pricePerLiterScaled: Long,
    ) : MoneyInput

    @HiddenFromObjC
    data class LitersAndTotal(
        val litersScaled: Long,
        val totalCostMinor: Long,
    ) : MoneyInput

    @HiddenFromObjC
    data class PriceAndTotal(
        val pricePerLiterScaled: Long,
        val totalCostMinor: Long,
    ) : MoneyInput
}

@HiddenFromObjC
data class CreateFuelEntryCommand(
    val vehicleId: EntityId,
    val date: Instant,
    val odometerKm: Long,
    val money: MoneyInput,
    val currency: CurrencyCode,
    val isFullTank: Boolean,
    val hasMissedEntries: Boolean,
    val notes: String?,
    val confirmations: Set<Confirmation>,
)

@HiddenFromObjC
data class UpdateFuelEntryCommand(
    val id: EntityId,
    val vehicleId: EntityId,
    val date: Instant,
    val odometerKm: Long,
    val money: MoneyInput,
    val currency: CurrencyCode,
    val isFullTank: Boolean,
    val hasMissedEntries: Boolean,
    val notes: String?,
    val confirmations: Set<Confirmation>,
)
