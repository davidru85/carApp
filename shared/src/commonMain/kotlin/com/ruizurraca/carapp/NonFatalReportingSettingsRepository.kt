package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.model.UserSettings
import com.ruizurraca.carapp.feature.session.domain.SettingsRepository
import com.ruizurraca.carapp.feature.session.domain.UpdateSettingsCommand
import kotlinx.coroutines.flow.Flow

/**
 * The graph boundary of `SettingsStateHolder` for `docs/CONTRACTS.md §20.3.1` (`D-243`). The holder
 * turns this repository's errors into its `UiMessage`, so the repository it is handed offers each
 * one to [NonFatalReporting] first. The analytics opt-in gate reads the undecorated repository: it
 * publishes no message.
 */
internal class NonFatalReportingSettingsRepository(
    private val delegate: SettingsRepository,
    internal val nonFatals: NonFatalReporting,
) : SettingsRepository {
    override val settings: Flow<Outcome<UserSettings, AppError>> = delegate.settings.reportingTo(nonFatals)

    override suspend fun updateSettings(command: UpdateSettingsCommand): Outcome<Unit, AppError> =
        delegate.updateSettings(command).alsoReportTo(nonFatals)
}
