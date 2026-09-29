package com.ruizurraca.carapp.integration.firebase.analytics

/**
 * An in-test stand-in for the Firebase SDK, so the tracker's contract is asserted without a runtime.
 *
 * It records exactly what reached the provider, which is the point: a rule that is enforced by the
 * tracker *before* the provider call is only observable here. [collectionStates] records every
 * command in order, so the fresh-install "off at construction" rule is distinguishable from a
 * provider that merely happens to start off.
 */
internal class RecordingGateway : AnalyticsGateway {
    val loggedEvents = mutableListOf<LoggedEvent>()
    val userProperties = mutableListOf<Map<String, String>>()
    val collectionStates = mutableListOf<Boolean>()

    override fun logEvent(
        name: String,
        parameters: Map<String, Any>,
    ) {
        loggedEvents += LoggedEvent(name, parameters)
    }

    override fun setUserProperty(
        name: String,
        value: String,
    ) {
        userProperties += mapOf(name to value)
    }

    override fun setCollectionEnabled(enabled: Boolean) {
        collectionStates += enabled
    }
}

internal data class LoggedEvent(
    val name: String,
    val parameters: Map<String, Any>,
)
