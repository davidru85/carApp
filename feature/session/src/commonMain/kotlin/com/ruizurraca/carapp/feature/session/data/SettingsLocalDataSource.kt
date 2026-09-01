package com.ruizurraca.carapp.feature.session.data

import com.ruizurraca.carapp.core.database.SettingsDatabaseAccess
import com.ruizurraca.carapp.core.database.SettingsDatabaseRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal data class LocalSettings(
    val currency: String,
    val distanceUnit: String,
    val volumeUnit: String,
    val analyticsEnabled: Boolean,
)

internal interface SettingsLocalDataSource {
    fun observeSettings(): Flow<LocalSettings?>

    suspend fun upsertSettings(settings: LocalSettings)
}

internal class SqlDelightSettingsLocalDataSource(
    private val databaseAccess: SettingsDatabaseAccess,
) : SettingsLocalDataSource {
    override fun observeSettings(): Flow<LocalSettings?> =
        databaseAccess.observeSettings().map { row -> row?.toLocalSettings() }

    override suspend fun upsertSettings(settings: LocalSettings) {
        databaseAccess.upsertSettings(settings.toDatabaseRow())
    }
}

private fun SettingsDatabaseRow.toLocalSettings() = LocalSettings(currency, distanceUnit, volumeUnit, analyticsEnabled)

private fun LocalSettings.toDatabaseRow() = SettingsDatabaseRow(currency, distanceUnit, volumeUnit, analyticsEnabled)
