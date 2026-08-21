package com.ruizurraca.carapp

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * Guards the datetime pin of `docs/versions-matrix.md` (`E0-06`).
 *
 * `docs/CONTRACTS.md §2` refers to the version matrix for the exact fully-qualified `Instant`
 * package, because kotlinx-datetime relocated the type. This test fails to compile if that pin
 * ever drifts:
 *
 * - the `kotlinx.datetime` imports resolve only when the pinned kotlinx-datetime artifact is on
 *   the common source set classpath;
 * - `toLocalDateTime` is declared on `kotlin.time.Instant`, so the call resolves only while the
 *   pinned package is exactly the one recorded in the matrix. A relocation back to
 *   `kotlinx.datetime.Instant` breaks the receiver type and fails the build instead of silently
 *   changing the canonical timestamp type.
 */
class PinnedInstantPackageTest {
    @Test
    fun pinnedInstantPackageIsTheOneRecordedInTheVersionMatrix() {
        val instant: Instant = Instant.parse("2026-08-21T10:15:30Z")
        val utc = instant.toLocalDateTime(TimeZone.UTC)

        assertEquals(2026, utc.year)
        assertEquals(10, utc.hour)
        assertEquals(15, utc.minute)
        assertEquals(30, utc.second)
    }
}
