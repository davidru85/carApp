package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.UnexpectedError
import com.ruizurraca.carapp.core.crash.CrashFieldAllowlist
import com.ruizurraca.carapp.core.crash.CrashReporter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach

/**
 * The `docs/CONTRACTS.md §20.3.1` `recordNonFatal` policy at the `:shared` chokepoints where an
 * `AppError` is about to become a `UiMessage` (`D-243`).
 *
 * It records exactly one kind of error, [UnexpectedError], with the single field `code`. The expected
 * `AuthError` leaves, connectivity-only `RemoteError` codes and validation warnings are never
 * recorded, and neither is any other typed error. A poisoned `SyncError` is not recorded here
 * either: the sync engine records it at the transition that created it, and recording it again where
 * it is shown would count one failure twice.
 *
 * The chokepoints are `toSessionUiMessage`, used by `SessionStateHolder` and `AccountDepartureFlow`,
 * and the repository decorators the graph places outermost on the vehicle, fuel-entry and settings
 * repositories it hands to their holders (`docs/TECHNICAL_PLAN.md §4` keeps feature presentation away
 * from `:core:crash`).
 */
internal class NonFatalReporting(
    internal val crashReporter: CrashReporter,
) {
    fun report(error: AppError) {
        // RED: the policy inverted, so every positive and every negative call-site test fails on its
        // assertion. GREEN records UnexpectedError alone.
        if (error !is UnexpectedError) crashReporter.recordNonFatal(error, emptyMap())
    }
}

/** Offers an `Err` to the policy and returns the result unchanged. */
internal fun <T> Outcome<T, AppError>.alsoReportTo(nonFatals: NonFatalReporting): Outcome<T, AppError> =
    also { result -> if (result is Outcome.Err) nonFatals.report(result.error) }

/** Offers every `Err` emission to the policy and emits it unchanged. */
internal fun <T> Flow<Outcome<T, AppError>>.reportingTo(nonFatals: NonFatalReporting): Flow<Outcome<T, AppError>> =
    onEach { result -> if (result is Outcome.Err) nonFatals.report(result.error) }
