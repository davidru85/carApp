@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package com.ruizurraca.carapp.feature.session.domain

import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.model.CurrencyCode
import com.ruizurraca.carapp.core.model.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
data class UpdateSettingsCommand(
    val currency: CurrencyCode?,
    val analyticsEnabled: Boolean?,
)

@HiddenFromObjC
interface SettingsRepository {
    val settings: Flow<Outcome<UserSettings, AppError>>

    suspend fun updateSettings(command: UpdateSettingsCommand): Outcome<Unit, AppError>
}
