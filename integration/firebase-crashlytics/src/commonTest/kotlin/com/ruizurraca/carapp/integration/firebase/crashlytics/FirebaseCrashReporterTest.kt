package com.ruizurraca.carapp.integration.firebase.crashlytics

import com.ruizurraca.carapp.core.common.SyncError
import com.ruizurraca.carapp.core.common.UnexpectedError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * `E4-04` (`D-21`, `D-241`, `D-243`): the Firebase-backed `CrashReporter` of
 * `docs/CONTRACTS.md §20.3.1`, asserted against a recording gateway, one behaviour per test.
 */
class FirebaseCrashReporterTest {
    private val unexpected =
        UnexpectedError(origin = ":core:sync", throwableClassName = "IllegalStateException")

    @Test
    fun aNewReporterRecordsNothingAndCommandsNothingBeforeTheOptIn() {
        val gateway = RecordingCrashlyticsGateway()
        val reporter = FirebaseCrashReporter(gateway = gateway)

        reporter.recordNonFatal(unexpected, mapOf("code" to "UNEXPECTED"))

        assertEquals(emptyList(), gateway.calls, "a disabled reporter MUST NOT reach the provider at all")
    }

    @Test
    fun aRecordedErrorSendsItsOwnCodeUnderTheReporterKey() {
        val gateway = RecordingCrashlyticsGateway()
        val reporter = FirebaseCrashReporter(enabled = true, gateway = gateway)

        reporter.recordNonFatal(unexpected, emptyMap())

        val keys = assertNotNull(gateway.customKeys.singleOrNull(), "one report reaches the provider")
        assertEquals("UNEXPECTED", keys["errorCode"])
    }

    @Test
    fun allowlistedFieldsReachTheProviderAsCustomKeys() {
        val gateway = RecordingCrashlyticsGateway()
        val reporter = FirebaseCrashReporter(enabled = true, gateway = gateway)

        reporter.recordNonFatal(
            SyncError.PermissionDenied,
            mapOf("entityType" to "VEHICLE", "code" to "REMOTE.PERMISSION_DENIED", "cycleId" to "cycle-1"),
        )

        val keys = assertNotNull(gateway.customKeys.singleOrNull(), "one report reaches the provider")
        assertEquals("VEHICLE", keys["entityType"])
        assertEquals("REMOTE.PERMISSION_DENIED", keys["code"])
        assertEquals("cycle-1", keys["cycleId"])
    }

    @Test
    fun fieldsOutsideTheAllowlistNeverReachTheProvider() {
        val gateway = RecordingCrashlyticsGateway()
        val reporter = FirebaseCrashReporter(enabled = true, gateway = gateway)
        val forbidden =
            mapOf(
                "uid" to "firebase-uid",
                "idToken" to "token",
                "notes" to "home",
                "odometerKm" to "123456",
                "totalCostMinor" to "6000",
                "payload" to "{\"name\":\"Roadster\"}",
            )

        reporter.recordNonFatal(unexpected, forbidden)

        val keys = assertNotNull(gateway.customKeys.singleOrNull(), "one report reaches the provider")
        assertEquals(emptySet(), keys.keys.intersect(forbidden.keys), "no forbidden key reaches the provider")
        assertEquals(
            emptyList(),
            keys.values.filter { value -> value in forbidden.values },
            "no forbidden value may travel under another key",
        )
    }

    @Test
    fun everyReportCarriesTheCompleteClosedKeySet() {
        val gateway = RecordingCrashlyticsGateway()
        val reporter = FirebaseCrashReporter(enabled = true, gateway = gateway)

        reporter.recordNonFatal(unexpected, mapOf("code" to "UNEXPECTED", "cycleId" to "cycle-1"))
        reporter.recordNonFatal(unexpected, mapOf("code" to "UNEXPECTED"))

        val second = assertNotNull(gateway.customKeys.getOrNull(1), "two reports reach the provider")
        assertEquals(
            setOf(
                "code",
                "cycleId",
                "entityType",
                "syncState",
                "syncTrigger",
                "consumptionInvalidReason",
                "confirmation",
                "errorCode",
            ),
            second.keys,
        )
        assertEquals("", second["cycleId"], "an absent field MUST NOT inherit the previous report's value")
    }

    @Test
    fun theRecordedExceptionCarriesTheErrorCodeAndNothingElse() {
        val gateway = RecordingCrashlyticsGateway()
        val reporter = FirebaseCrashReporter(enabled = true, gateway = gateway)

        reporter.recordNonFatal(SyncError.PayloadPoisoned, mapOf("code" to "REMOTE.UNKNOWN"))

        val exception = assertIs<RecordedAppError>(gateway.exceptions.singleOrNull())
        assertEquals("SYNC.PAYLOAD_POISONED", exception.message)
        assertNull(exception.cause)
    }

    @Test
    fun theKeysAreSetBeforeTheExceptionIsRecorded() {
        val gateway = RecordingCrashlyticsGateway()
        val reporter = FirebaseCrashReporter(enabled = true, gateway = gateway)

        reporter.recordNonFatal(unexpected, emptyMap())

        // The provider attaches the keys present at the moment of the event, so the order is the rule.
        assertEquals(listOf("setCustomKeys", "recordException"), gateway.calls)
    }

    @Test
    fun enablingCommandsProviderCollectionOn() {
        val gateway = RecordingCrashlyticsGateway()
        val reporter = FirebaseCrashReporter(gateway = gateway)

        reporter.setEnabled(true)

        assertEquals(listOf("setCollectionEnabled:true"), gateway.calls)
    }

    @Test
    fun disablingCommandsCollectionOffAndDeletesTheStoredReports() {
        val gateway = RecordingCrashlyticsGateway()
        val reporter = FirebaseCrashReporter(gateway = gateway)

        reporter.setEnabled(false)

        assertEquals(listOf("setCollectionEnabled:false", "deleteUnsentReports"), gateway.calls)
    }

    @Test
    fun disablingStopsRecordingImmediately() {
        val gateway = RecordingCrashlyticsGateway()
        val reporter = FirebaseCrashReporter(enabled = true, gateway = gateway)
        reporter.setEnabled(false)
        gateway.calls.clear()

        reporter.recordNonFatal(unexpected, emptyMap())

        assertEquals(emptyList(), gateway.calls)
    }

    @Test
    fun nothingRecordedWhileDisabledIsReplayedOnEnable() {
        val gateway = RecordingCrashlyticsGateway()
        val reporter = FirebaseCrashReporter(gateway = gateway)

        reporter.recordNonFatal(unexpected, emptyMap())
        reporter.setEnabled(true)

        assertEquals(listOf("setCollectionEnabled:true"), gateway.calls)
    }
}
