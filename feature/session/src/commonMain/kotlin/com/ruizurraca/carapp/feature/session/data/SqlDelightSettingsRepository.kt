@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package com.ruizurraca.carapp.feature.session.data

import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.LocaleProvider
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.database.SettingsDatabaseAccess
import com.ruizurraca.carapp.core.model.UserSettings
import com.ruizurraca.carapp.feature.session.domain.SettingsRepository
import com.ruizurraca.carapp.feature.session.domain.UpdateSettingsCommand
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
class SqlDelightSettingsRepository internal constructor(
    private val localDataSource: SettingsLocalDataSource,
    private val localeProvider: LocaleProvider,
) : SettingsRepository {
    constructor(
        databaseAccess: SettingsDatabaseAccess,
        localeProvider: LocaleProvider,
    ) : this(SqlDelightSettingsLocalDataSource(databaseAccess), localeProvider)

    override val settings: Flow<Outcome<UserSettings, AppError>> =
        flow {
            error(
                "E1-10 settings bootstrap is not implemented for " +
                    "$localDataSource/$localeProvider",
            )
        }

    override suspend fun updateSettings(command: UpdateSettingsCommand): Outcome<Unit, AppError> =
        error("E1-10 settings update is not implemented for $command/$localDataSource")
}
