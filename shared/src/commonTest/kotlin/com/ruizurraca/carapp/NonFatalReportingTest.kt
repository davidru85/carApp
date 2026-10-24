package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.AuthError
import com.ruizurraca.carapp.core.common.RemoteError
import com.ruizurraca.carapp.core.common.SyncError
import com.ruizurraca.carapp.core.common.UnexpectedError
import com.ruizurraca.carapp.core.common.ValidationWarning
import com.ruizurraca.carapp.core.testing.RecordingCrashReporter
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `E4-04` (`D-243`): the `docs/CONTRACTS.md §20.3.1` policy of the `:shared` chokepoints, one rule
 * per test.
 */
class NonFatalReportingTest {
    private val crashReporter = RecordingCrashReporter()
    private val nonFatals = NonFatalReporting(crashReporter)

    @Test
    fun anUnexpectedErrorIsRecordedOnceWithItsCodeAsTheOnlyField() {
        val error = UnexpectedError(origin = ":shared", throwableClassName = "IllegalStateException")

        nonFatals.report(error)

        assertEquals(
            listOf<Pair<AppError, Map<String, String>>>(error to mapOf("code" to "UNEXPECTED")),
            crashReporter.nonFatals,
        )
    }

    @Test
    fun theExpectedAuthLeavesAreNeverRecorded() {
        listOf(AuthError.Cancelled, AuthError.RequiresRecentLogin, AuthError.CredentialAlreadyInUse)
            .forEach(nonFatals::report)

        assertEquals(emptyList(), crashReporter.nonFatals)
    }

    @Test
    fun connectivityOnlyRemoteErrorsAreNeverRecorded() {
        listOf(RemoteError.Unavailable, RemoteError.DeadlineExceeded).forEach(nonFatals::report)

        assertEquals(emptyList(), crashReporter.nonFatals)
    }

    @Test
    fun validationWarningsAreNeverRecorded() {
        listOf(
            ValidationWarning.OdometerInconsistent(previousOdometerKm = 1_000, enteredOdometerKm = 900),
            ValidationWarning.PendingSyncBeforeSignOut(pendingCount = 2),
        ).forEach(nonFatals::report)

        assertEquals(emptyList(), crashReporter.nonFatals)
    }

    @Test
    fun aPoisonedSyncErrorIsNotRecordedAgainWhereItIsShown() {
        // The engine records a poisoned transition where it happens (`AppGraph` binds `onPoisoned`);
        // recording it again at the message would count one failure twice.
        listOf(
            SyncError.PermissionDenied,
            SyncError.ValidationRejected,
            SyncError.PayloadPoisoned,
            SyncError.ConflictUnresolved,
        ).forEach(nonFatals::report)

        assertEquals(emptyList(), crashReporter.nonFatals)
    }
}
