package com.ruizurraca.carapp.integration.firebase.crashlytics

/**
 * An in-test stand-in for the Firebase Crashlytics SDK, so the reporter's contract is asserted
 * without a runtime.
 *
 * [calls] records every command in order. That is what distinguishes "the keys were set before the
 * exception was recorded" and "construction commanded nothing" from a provider that merely ends in
 * the right state.
 */
internal class RecordingCrashlyticsGateway : CrashlyticsGateway {
    val calls = mutableListOf<String>()
    val customKeys = mutableListOf<Map<String, String>>()
    val exceptions = mutableListOf<Throwable>()

    override fun setCustomKeys(keys: Map<String, String>) {
        calls += "setCustomKeys"
        customKeys += keys
    }

    override fun recordException(exception: Throwable) {
        calls += "recordException"
        exceptions += exception
    }

    override fun setCollectionEnabled(enabled: Boolean) {
        calls += "setCollectionEnabled:$enabled"
    }

    override fun deleteUnsentReports() {
        calls += "deleteUnsentReports"
    }
}
