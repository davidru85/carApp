package com.ruizurraca.carapp.core.crash

import com.ruizurraca.carapp.core.common.AppError

/**
 * Crash reporting abstraction of `docs/CONTRACTS.md §20.3.1`.
 *
 * `CrashReporter` is a required graph dependency from Phase 0 so that graph construction never
 * changes shape when release hardening begins (`§11.6`). Firebase Crashlytics arrives only in
 * Phase 4, inside `:integration:firebase-crashlytics`, and is bound through `:wiring:firebase`.
 *
 * Trigger policy, normative in `§20.3.1`: [recordNonFatal] MUST be called for every
 * `UnexpectedError` and for every poisoned-sync transition. It MUST NOT be called for validation
 * warnings, for the expected `AuthError` leaves (`Cancelled`, `RequiresRecentLogin`,
 * `CredentialAlreadyInUse`), or for connectivity-only `RemoteError` codes.
 *
 * [fields] follows the same allowlist as `Logger` (`§17`): stable codes, enum names, counts,
 * durations and booleans, never raw user data.
 *
 * This module MUST NOT contain `expect`/`actual` declarations; platform implementations live in
 * `:integration:*`.
 */
interface CrashReporter {
    fun recordNonFatal(error: AppError, fields: Map<String, String>)

    fun setEnabled(enabled: Boolean)
}

/**
 * The default implementation for tests and local builds, and the default fake used by
 * `:core:testing` (`§20.3.1`). It records nothing and buffers nothing.
 */
object NoOpCrashReporter : CrashReporter {
    override fun recordNonFatal(error: AppError, fields: Map<String, String>) = Unit

    override fun setEnabled(enabled: Boolean) = Unit
}
