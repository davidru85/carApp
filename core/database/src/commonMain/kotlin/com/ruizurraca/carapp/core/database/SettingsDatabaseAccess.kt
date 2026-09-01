package com.ruizurraca.carapp.core.database

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.coroutines.EmptyCoroutineContext

data class SettingsDatabaseRow(
    val currency: String,
    val distanceUnit: String,
    val volumeUnit: String,
    val analyticsEnabled: Boolean,
)

/** Keeps SQLDelight-generated settings types inside `:core:database`. */
class SettingsDatabaseAccess(
    private val database: AppDatabase,
) {
    fun observeSettings(): Flow<SettingsDatabaseRow?> =
        database.databaseQueries
            .selectSettings()
            .asFlow()
            .mapToOneOrNull(EmptyCoroutineContext)
            .map { row ->
                row?.let {
                    SettingsDatabaseRow(
                        currency = it.currency,
                        distanceUnit = it.distanceUnit,
                        volumeUnit = it.volumeUnit,
                        analyticsEnabled = it.analyticsEnabled != 0L,
                    )
                }
            }

    suspend fun upsertSettings(settings: SettingsDatabaseRow) {
        database.databaseQueries.upsertSettings(
            currency = settings.currency,
            distanceUnit = settings.distanceUnit,
            volumeUnit = settings.volumeUnit,
            analyticsEnabled = if (settings.analyticsEnabled) 1L else 0L,
        )
    }

    suspend fun deleteSettings() {
        database.databaseQueries.deleteSettings()
    }
}
