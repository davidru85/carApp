package com.ruizurraca.carapp.feature.session.data

import com.ruizurraca.carapp.core.common.LocaleInfo
import com.ruizurraca.carapp.core.common.LocaleProvider
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.PersistenceError
import com.ruizurraca.carapp.core.common.ValidationError
import com.ruizurraca.carapp.core.database.SettingsDatabaseAccess
import com.ruizurraca.carapp.core.model.CurrencyCode
import com.ruizurraca.carapp.core.model.DistanceUnit
import com.ruizurraca.carapp.core.model.UserSettings
import com.ruizurraca.carapp.core.model.VolumeUnit
import com.ruizurraca.carapp.core.testing.FakeLocaleProvider
import com.ruizurraca.carapp.core.testing.InMemoryDatabaseFactory
import com.ruizurraca.carapp.feature.session.domain.UpdateSettingsCommand
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SqlDelightSettingsRepositoryTest {
    @Test
    fun sqlDelightObservationCreatesLocaleDefaults() =
        runTest {
            val owningFactory = InMemoryDatabaseFactory()
            val handle = owningFactory.create()
            val repository =
                SqlDelightSettingsRepository(
                    SettingsDatabaseAccess(handle.database),
                    localeProvider("USD"),
                )

            try {
                assertEquals(
                    UserSettings(CurrencyCode("USD"), DistanceUnit.KM, VolumeUnit.LITER, false),
                    assertIs<Outcome.Ok<UserSettings>>(repository.settings.first()).value,
                )
            } finally {
                handle.close()
                owningFactory.close()
            }
        }

    @Test
    fun closedGraphGatePreventsBootstrapDefaultWrite() =
        runTest {
            val local = FiniteMissingSettingsLocalDataSource()
            var localeReads = 0
            val repository =
                SqlDelightSettingsRepository(
                    localDataSource = local,
                    localeProvider =
                        LocaleProvider {
                            localeReads += 1
                            error("The locale must not be read when default creation is disabled")
                        },
                    canCreateDefaults = { false },
                )

            assertEquals(emptyList(), repository.settings.toList())
            assertEquals(0, local.writeCount)
            assertEquals(0, localeReads)
        }

    @Test
    fun firstObservationCreatesLocaleDefaultsWithAnalyticsDisabled() =
        runTest {
            val local = FakeSettingsLocalDataSource()
            val repository = repository(local, localeCurrency = "USD")

            val result = repository.settings.first()

            assertEquals(
                UserSettings(CurrencyCode("USD"), DistanceUnit.KM, VolumeUnit.LITER, false),
                assertIs<Outcome.Ok<UserSettings>>(result).value,
            )
            assertEquals(1, local.writes.size)
        }

    @Test
    fun unsupportedLocaleCurrencyFallsBackToEur() =
        runTest {
            val repository = repository(FakeSettingsLocalDataSource(), localeCurrency = "JPY")

            val result = repository.settings.first()

            assertEquals("EUR", assertIs<Outcome.Ok<UserSettings>>(result).value.currency.value)
        }

    @Test
    fun partialUpdatesPreserveTheOtherField() =
        runTest {
            val local =
                FakeSettingsLocalDataSource(
                    LocalSettings("EUR", "KM", "LITER", analyticsEnabled = false),
                )
            val repository = repository(local)

            assertEquals(
                Outcome.Ok(Unit),
                repository.updateSettings(UpdateSettingsCommand(CurrencyCode("GBP"), null)),
            )
            assertEquals(
                Outcome.Ok(Unit),
                repository.updateSettings(UpdateSettingsCommand(null, analyticsEnabled = true)),
            )

            assertEquals(
                LocalSettings("GBP", "KM", "LITER", analyticsEnabled = true),
                local.current.value,
            )
        }

    @Test
    fun noOpReturnsTypedErrorAndMutatesNothing() =
        runTest {
            val existing = LocalSettings("EUR", "KM", "LITER", analyticsEnabled = false)
            val local = FakeSettingsLocalDataSource(existing)
            val repository = repository(local)

            val result = repository.updateSettings(UpdateSettingsCommand(null, null))

            assertEquals(ValidationError.NoOp, assertIs<Outcome.Err<*>>(result).error)
            assertEquals(emptyList(), local.writes)
            assertEquals(existing, local.current.value)
        }

    @Test
    fun unsupportedExplicitCurrencyReturnsInvalidUnitAndMutatesNothing() =
        runTest {
            val existing = LocalSettings("EUR", "KM", "LITER", analyticsEnabled = false)
            val local = FakeSettingsLocalDataSource(existing)
            val repository = repository(local)

            val result =
                repository.updateSettings(
                    UpdateSettingsCommand(CurrencyCode("JPY"), analyticsEnabled = true),
                )

            assertEquals(ValidationError.InvalidUnit("JPY"), assertIs<Outcome.Err<*>>(result).error)
            assertEquals(emptyList(), local.writes)
            assertEquals(existing, local.current.value)
        }

    @Test
    fun repositoryAccessAfterDeletionRecreatesLocaleDefaults() =
        runTest {
            val local =
                FakeSettingsLocalDataSource(
                    LocalSettings("GBP", "KM", "LITER", analyticsEnabled = true),
                )
            val repository = repository(local, localeCurrency = "USD")
            assertEquals("GBP", assertIs<Outcome.Ok<UserSettings>>(repository.settings.first()).value.currency.value)

            local.delete()
            val recreated = assertIs<Outcome.Ok<UserSettings>>(repository.settings.first()).value

            assertEquals(UserSettings(CurrencyCode("USD"), DistanceUnit.KM, VolumeUnit.LITER, false), recreated)
        }

    @Test
    fun persistenceFailuresMapToStableErrors() =
        runTest {
            val readFailure = SqlDelightSettingsRepository(FailingSettingsLocalDataSource, localeProvider())
            assertEquals(
                PersistenceError.DatabaseUnavailable,
                assertIs<Outcome.Err<*>>(readFailure.settings.first()).error,
            )

            val writeFailure =
                SqlDelightSettingsRepository(
                    WriteFailingSettingsLocalDataSource(
                        LocalSettings("EUR", "KM", "LITER", analyticsEnabled = false),
                    ),
                    localeProvider(),
                )
            assertEquals(
                PersistenceError.TransactionFailed,
                assertIs<Outcome.Err<*>>(
                    writeFailure.updateSettings(UpdateSettingsCommand(null, analyticsEnabled = true)),
                ).error,
            )
        }

    private fun repository(
        local: SettingsLocalDataSource,
        localeCurrency: String = "EUR",
    ) = SqlDelightSettingsRepository(local, localeProvider(localeCurrency))
}

private open class FakeSettingsLocalDataSource(
    initial: LocalSettings? = null,
) : SettingsLocalDataSource {
    val current = MutableStateFlow(initial)
    val writes = mutableListOf<LocalSettings>()

    override fun observeSettings(): Flow<LocalSettings?> = current

    override suspend fun upsertSettings(settings: LocalSettings) {
        writes += settings
        current.value = settings
    }

    fun delete() {
        current.value = null
    }
}

private object FailingSettingsLocalDataSource : SettingsLocalDataSource {
    override fun observeSettings(): Flow<LocalSettings?> = flow { throw SettingsTestFailure() }

    override suspend fun upsertSettings(settings: LocalSettings) = throw SettingsTestFailure()
}

private class FiniteMissingSettingsLocalDataSource : SettingsLocalDataSource {
    var writeCount = 0
        private set

    override fun observeSettings(): Flow<LocalSettings?> = flowOf(null)

    override suspend fun upsertSettings(settings: LocalSettings) {
        writeCount += 1
    }
}

private class WriteFailingSettingsLocalDataSource(
    initial: LocalSettings,
) : FakeSettingsLocalDataSource(initial) {
    override suspend fun upsertSettings(settings: LocalSettings) = throw SettingsTestFailure()
}

private class SettingsTestFailure : RuntimeException()

private fun localeProvider(currency: String = "EUR") =
    FakeLocaleProvider(LocaleInfo("en-US", "US", CurrencyCode(currency)))
