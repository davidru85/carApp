package com.ruizurraca.carapp.integration.firebase.crashlytics

import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.crash.CrashReporter

/**
 * RED: declared without behaviour so the `E4-04` reporter tests compile and execute. The public
 * no-argument constructor and the GitLive gateway arrive with the behaviour.
 */
@Suppress("UnusedPrivateProperty", "UnusedParameter")
class FirebaseCrashReporter internal constructor(
    enabled: Boolean = false,
    private val gateway: CrashlyticsGateway,
) : CrashReporter {
    override fun recordNonFatal(
        error: AppError,
        fields: Map<String, String>,
    ) = Unit

    override fun setEnabled(enabled: Boolean) = Unit
}

/** The throwable a non-fatal report carries. */
internal class RecordedAppError(
    code: String,
) : Exception(code)
