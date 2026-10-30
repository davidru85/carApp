package com.ruizurraca.carapp.integration.firebase.crashlytics

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.crashlytics.FirebaseCrashlytics
import dev.gitlive.firebase.crashlytics.crashlytics

/**
 * The only place a Firebase Crashlytics type appears (`D-179`, `D-21`).
 *
 * It is `internal`, so no GitLive type crosses this module's boundary: `:wiring:firebase` constructs
 * the reporter through its public constructor and never names [FirebaseCrashlytics].
 *
 * The provider is resolved on every call rather than at construction, so building the reporter
 * touches no SDK; both hosts configure `FirebaseApp` before the graph exists.
 *
 * Provider exceptions are not caught here. The reporter owns the failure policy, so this adapter
 * stays a thin, total translation of the gateway vocabulary into the SDK's.
 */
internal class GitLiveCrashlyticsGateway : CrashlyticsGateway {
    private val provider: FirebaseCrashlytics get() = Firebase.crashlytics

    override fun setCustomKeys(keys: Map<String, String>) {
        provider.setCustomKeys(keys)
    }

    override fun recordException(exception: Throwable) {
        provider.recordException(exception)
    }

    override fun setCollectionEnabled(enabled: Boolean) {
        provider.setCrashlyticsCollectionEnabled(enabled)
    }

    override fun deleteUnsentReports() {
        provider.deleteUnsentReports()
    }
}
