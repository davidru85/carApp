@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package com.ruizurraca.carapp.core.common

import kotlin.native.HiddenFromObjC

/**
 * The logging contract of `docs/CONTRACTS.md §17`.
 *
 * **Redaction is the implementation's responsibility, not the caller's.** The implementation
 * accepts entity IDs as `String` and the caller MUST NOT pre-redact. Redaction is decided from the
 * injected `isDebugBuild` flag: debug builds may log entity IDs in full; release builds log the
 * first 8 characters followed by an ellipsis, and never log the UID at any length.
 *
 * [fields] values come from a closed allowlist and never carry raw user data: `AppError.code`,
 * `SyncState` / `EntityType` / `SyncTrigger` / `ConsumptionInvalidReason` / `Confirmation` enum
 * names, `cycleId`, integer counts and durations, and `Boolean` flags.
 *
 * Logs MUST never include ID tokens or credentials, raw Firestore payloads, notes, exact odometer
 * values, exact costs, or the Firebase UID in release builds. `Throwable.message` and
 * `Throwable.stackTrace` MUST be redacted to stable codes and MUST NOT be logged as text.
 *
 * This is not an analytics or crash-reporting API. Logging events MUST NOT be treated as
 * `AnalyticsEvent` values or crash reports.
 */
@HiddenFromObjC
interface Logger {
    fun log(
        level: LogLevel,
        tag: String,
        message: String,
        fields: Map<String, String>,
        throwable: Throwable?,
    )
}
