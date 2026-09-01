package com.ruizurraca.carapp.core.database

import kotlinx.coroutines.flow.Flow

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
        error("E1-10 settings observation is not implemented for ${database.databaseQueries}")

    suspend fun upsertSettings(settings: SettingsDatabaseRow) {
        error("E1-10 settings persistence is not implemented for ${settings.currency}")
    }

    suspend fun deleteSettings() {
        error("E1-10 settings deletion is not implemented for ${database.databaseQueries}")
    }
}
