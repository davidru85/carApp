@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package com.ruizurraca.carapp.feature.session.data

import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.LocaleProvider
import com.ruizurraca.carapp.core.common.MinorUnits
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.PersistenceError
import com.ruizurraca.carapp.core.common.ValidationError
import com.ruizurraca.carapp.core.database.SettingsDatabaseAccess
import com.ruizurraca.carapp.core.model.CurrencyCode
import com.ruizurraca.carapp.core.model.DistanceUnit
import com.ruizurraca.carapp.core.model.UserSettings
import com.ruizurraca.carapp.core.model.VolumeUnit
import com.ruizurraca.carapp.feature.session.domain.SettingsRepository
import com.ruizurraca.carapp.feature.session.domain.UpdateSettingsCommand
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.transform
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
        localDataSource
            .observeSettings()
            .transform<LocalSettings?, Outcome<UserSettings, AppError>> { localSettings ->
                if (localSettings == null) {
                    try {
                        localDataSource.upsertSettings(defaultSettings())
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (_: Throwable) {
                        emit(Outcome.Err(PersistenceError.TransactionFailed))
                    }
                } else {
                    emit(Outcome.Ok(localSettings.toDomainSettings()))
                }
            }.catch { exception ->
                if (exception is CancellationException) throw exception
                emit(Outcome.Err(PersistenceError.DatabaseUnavailable))
            }

    override suspend fun updateSettings(command: UpdateSettingsCommand): Outcome<Unit, AppError> {
        if (command.currency == null && command.analyticsEnabled == null) {
            return Outcome.Err(ValidationError.NoOp)
        }
        command.currency?.let { currency ->
            if (MinorUnits.factorFor(currency) != TWO_DECIMAL_MINOR_UNIT_FACTOR) {
                return Outcome.Err(ValidationError.InvalidUnit(currency.value))
            }
        }

        val current = settings.first()
        if (current is Outcome.Err) return current
        current as Outcome.Ok
        val updated =
            current.value.copy(
                currency = command.currency ?: current.value.currency,
                analyticsEnabled = command.analyticsEnabled ?: current.value.analyticsEnabled,
            )

        return try {
            localDataSource.upsertSettings(updated.toLocalSettings())
            Outcome.Ok(Unit)
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Throwable) {
            Outcome.Err(PersistenceError.TransactionFailed)
        }
    }

    private fun defaultSettings(): LocalSettings {
        val localeCurrency = localeProvider.current().suggestedCurrency
        val currency =
            localeCurrency.takeIf { MinorUnits.factorFor(it) == TWO_DECIMAL_MINOR_UNIT_FACTOR }
                ?: DEFAULT_CURRENCY
        return LocalSettings(
            currency = currency.value,
            distanceUnit = DistanceUnit.KM.name,
            volumeUnit = VolumeUnit.LITER.name,
            analyticsEnabled = false,
        )
    }

    private companion object {
        val DEFAULT_CURRENCY = CurrencyCode("EUR")
        const val TWO_DECIMAL_MINOR_UNIT_FACTOR = 100
    }
}

private fun LocalSettings.toDomainSettings() =
    UserSettings(
        currency = CurrencyCode(currency),
        distanceUnit = DistanceUnit.valueOf(distanceUnit),
        volumeUnit = VolumeUnit.valueOf(volumeUnit),
        analyticsEnabled = analyticsEnabled,
    )

private fun UserSettings.toLocalSettings() =
    LocalSettings(
        currency = currency.value,
        distanceUnit = distanceUnit.name,
        volumeUnit = volumeUnit.name,
        analyticsEnabled = analyticsEnabled,
    )
