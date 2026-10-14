package com.ruizurraca.carapp.integration.firebase.analytics

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.analytics.FirebaseAnalytics
import dev.gitlive.firebase.analytics.analytics

/**
 * The only place a Firebase Analytics type appears (`D-179`).
 *
 * It is `internal`, so no GitLive type crosses this module's boundary: `:wiring:firebase` constructs
 * [FirebaseAnalyticsTracker] through its public constructor and never names [FirebaseAnalytics].
 *
 * Provider exceptions are not caught here. [FirebaseAnalyticsTracker] owns the failure policy, so
 * classification happens once and this adapter stays a thin, total translation of the gateway
 * vocabulary into the SDK's.
 */
internal class GitLiveAnalyticsGateway : AnalyticsGateway {
    private val provider: FirebaseAnalytics get() = Firebase.analytics

    override fun logEvent(
        name: String,
        parameters: Map<String, Any>,
    ) {
        provider.logEvent(name, parameters)
    }

    override fun setUserProperty(
        name: String,
        value: String,
    ) {
        provider.setUserProperty(name, value)
    }

    override fun setCollectionEnabled(enabled: Boolean) {
        provider.setAnalyticsCollectionEnabled(enabled)
    }
}
