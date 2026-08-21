package com.ruizurraca.carapp.core.common

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Guards the named constants of `docs/CONTRACTS.md §20.0.1`. Their values are referenced by
 * section number across the contract, so a silent change here would desynchronise the document
 * from the code without failing anything else.
 */
class ConstantsTest {
    @Test
    fun namedConstantsMatchTheContract() {
        assertEquals(1, CLIENT_MAX_SCHEMA_VERSION)
        assertEquals(10, MAX_RETRYABLE_ATTEMPTS)
        assertEquals(5_000, MAX_ENTRIES_IN_MEMORY)
        assertEquals("carapp-sync", SYNC_WORK)
        assertEquals(5_000L, STATE_HOLDER_TIMEOUT_MS)
        assertEquals(300_000L, FOREGROUND_RESUME_THRESHOLD_MS)
        assertEquals(300_000L, FRESH_LOGIN_THRESHOLD_MS)
    }

    @Test
    fun connectivityErrorCodesAreExactlyTheTwoThatDoNotConsumeThePoisonBudget() {
        assertEquals(setOf("REMOTE.UNAVAILABLE", "REMOTE.DEADLINE_EXCEEDED"), CONNECTIVITY_ERROR_CODES)
    }

    @Test
    fun connectivityErrorCodesMatchTheRemoteErrorLeavesTheyName() {
        assertEquals(
            CONNECTIVITY_ERROR_CODES,
            setOf(RemoteError.Unavailable.code, RemoteError.DeadlineExceeded.code),
            "The code strings must stay in step with the RemoteError leaves they refer to",
        )
    }
}
