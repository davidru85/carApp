package com.ruizurraca.carapp.integration.firebase.analytics

import com.ruizurraca.carapp.core.analytics.AnalyticsEvent
import com.ruizurraca.carapp.core.analytics.AnalyticsTracker
import com.ruizurraca.carapp.core.analytics.AnalyticsUserProperties

/**
 * The Firebase Analytics boundary, narrow on purpose.
 *
 * It exists so that [FirebaseAnalyticsTracker] — the class that enforces the `§16.1` event mapping,
 * the opt-in gate and the payload allowlist — is exercised in `commonTest` without a Firebase
 * runtime, the same isolation `FirebaseAuthGateway` and `FirestoreGateway` give their integrations.
 *
 * The three methods mirror exactly the three provider capabilities the tracker commands: one event,
 * one user property and the collection switch. `Firebase.analytics` exposes `setUserId` and
 * `setConsent` too, and neither is reachable from here: `§16.1` forbids the UID in a payload, so
 * the boundary does not offer a way to set one.
 */
internal interface AnalyticsGateway {
    fun logEvent(
        name: String,
        parameters: Map<String, Any>,
    )

    fun setUserProperty(
        name: String,
        value: String,
    )

    fun setCollectionEnabled(enabled: Boolean)
}
