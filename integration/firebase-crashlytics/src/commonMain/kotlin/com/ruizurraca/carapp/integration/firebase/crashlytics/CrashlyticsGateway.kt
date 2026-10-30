package com.ruizurraca.carapp.integration.firebase.crashlytics

/**
 * The Firebase Crashlytics boundary, narrow on purpose.
 *
 * It exists so that the reporter of this module — the class that enforces the `§20.3.1` opt-in gate
 * and the `§17` field allowlist — is exercised in `commonTest` without a Firebase runtime, the same
 * isolation `AnalyticsGateway` gives the analytics integration.
 *
 * The four methods are the only provider capabilities the reporter commands. The SDK also offers a
 * user identifier and free-text log lines, and neither is reachable from here: `§20.3.1` and
 * ADR-0023 forbid the UID and free text in a crash report, so the boundary offers no way to send
 * them.
 */
internal interface CrashlyticsGateway {
    fun setCustomKeys(keys: Map<String, String>)

    fun recordException(exception: Throwable)

    fun setCollectionEnabled(enabled: Boolean)

    fun deleteUnsentReports()
}
