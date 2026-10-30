package com.ruizurraca.carapp.integration.firebase.crashlytics

import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.crash.CrashFieldAllowlist
import com.ruizurraca.carapp.core.crash.CrashReporter
import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException

/**
 * The Firebase-backed `CrashReporter` of `docs/CONTRACTS.md §20.3.1` (`D-21`, ADR-0023).
 *
 * This is the only place in the repository where a crash-reporting provider is reached directly. The
 * provider is behind [CrashlyticsGateway], so every rule this class enforces is testable without a
 * Firebase runtime:
 *
 * 1. **Nothing is recorded until the opt-in** (`D-241`). A new reporter is disabled; while
 *    disabled, [recordNonFatal] drops its argument instead of holding it, so enabling later replays
 *    nothing. Construction commands nothing: the hosts disable collection at process start and the
 *    graph's opt-in gate commands the stored choice at every start.
 * 2. **A disable also discards what the provider stored while off.** The SDK keeps crash reports on
 *    the device while collection is off and applies a disable from the next launch, so
 *    [setEnabled] `false` deletes the stored reports: an opt-in must never upload data captured
 *    before it.
 * 3. **The payload is closed** (`D-243`). The fields pass [CrashFieldAllowlist]; the recorded
 *    error's own code travels under the reporter's key `errorCode`; every allowlisted key is set on
 *    every report, blank when absent, because provider custom keys outlive the report that set
 *    them; the throwable's message is the `AppError.code` alone; and no user identifier is ever
 *    set — [CrashlyticsGateway] cannot express one.
 *
 * A provider failure never propagates: both entry points are non-suspending `Unit` functions with no
 * error channel, called from failure paths, so a throw would turn a recorded failure into an
 * unhandled one.
 */
class FirebaseCrashReporter internal constructor(
    enabled: Boolean = false,
    private val gateway: CrashlyticsGateway,
) : CrashReporter {
    constructor() : this(
        enabled = false,
        gateway = GitLiveCrashlyticsGateway(),
    )

    // Written by the graph's opt-in collector and read by every thread that records a failure.
    @Volatile
    private var enabled = enabled

    override fun recordNonFatal(
        error: AppError,
        fields: Map<String, String>,
    ) {
        if (!enabled) return
        val keys = BLANK_KEYS + CrashFieldAllowlist.filter(fields) + (ERROR_CODE to error.code)
        safely {
            gateway.setCustomKeys(keys)
            gateway.recordException(RecordedAppError(error.code))
        }
    }

    override fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        safely {
            gateway.setCollectionEnabled(enabled)
            if (!enabled) gateway.deleteUnsentReports()
        }
    }

    /**
     * `TooGenericExceptionCaught` is suppressed because the policy is to survive an arbitrary provider
     * failure. Cancellation is rethrown from its own catch so a cancelled coroutine is never mistaken
     * for a provider failure.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private inline fun safely(block: () -> Unit) {
        try {
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // A crash reporter that cannot reach its provider has nowhere to report that; the product
            // path it was called from continues (`D-21`, best-effort observability).
        }
    }

    private companion object {
        /** The reporter's own key: the `AppError.code` of the recorded error. */
        const val ERROR_CODE = "errorCode"

        /** Every allowlisted key, blank, so a report never inherits a previous report's value. */
        val BLANK_KEYS: Map<String, String> = CrashFieldAllowlist.KEYS.associateWith { "" }
    }
}

/**
 * The throwable a non-fatal report carries. Its message is the stable `AppError.code` and nothing
 * else: `§17` forbids exception text, and the error the reporter receives carries no throwable.
 */
internal class RecordedAppError(
    code: String,
) : Exception(code)
