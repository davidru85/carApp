package com.ruizurraca.carapp.core.crash

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `E4-04` (`D-243`): the `docs/CONTRACTS.md §17` allowlist every crash report's fields pass through.
 * One test per rule: what passes, what is dropped, and how a key is matched.
 */
class CrashFieldAllowlistTest {
    @Test
    fun everyAllowlistedKeyPassesWithItsValueUnchanged() {
        val allowlisted =
            mapOf(
                "code" to "REMOTE.PERMISSION_DENIED",
                "cycleId" to "cycle-1",
                "entityType" to "VEHICLE",
                "syncState" to "FAILED_POISONED",
                "syncTrigger" to "PullToRefresh",
                "consumptionInvalidReason" to "MissedEntriesInSegment",
                "confirmation" to "DeleteAccount",
            )

        assertEquals(allowlisted, CrashFieldAllowlist.filter(allowlisted))
    }

    @Test
    fun everyKeyOutsideTheAllowlistIsDroppedBeforeAProviderSeesIt() {
        // One key per category `§17` and `§20.3.1` forbid: the UID, a token, notes, an exact
        // odometer value, an exact cost, a raw payload and a raw entity ID.
        val forbidden =
            mapOf(
                "uid" to "firebase-uid",
                "idToken" to "token",
                "notes" to "home",
                "odometerKm" to "123456",
                "totalCostMinor" to "6000",
                "payload" to "{\"name\":\"Roadster\"}",
                "vehicleId" to "c0ffee00-0000-4000-8000-000000000000",
            )

        assertEquals(
            mapOf("code" to "UNEXPECTED"),
            CrashFieldAllowlist.filter(forbidden + ("code" to "UNEXPECTED")),
        )
    }

    @Test
    fun keysAreMatchedExactlyAndCaseSensitively() {
        val nearMisses =
            mapOf(
                "Code" to "UNEXPECTED",
                "cycleid" to "cycle-1",
                "entity_type" to "VEHICLE",
                " code" to "UNEXPECTED",
            )

        assertEquals(emptyMap(), CrashFieldAllowlist.filter(nearMisses))
    }
}
