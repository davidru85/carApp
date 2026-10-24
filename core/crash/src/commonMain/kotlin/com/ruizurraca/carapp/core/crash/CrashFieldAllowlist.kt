package com.ruizurraca.carapp.core.crash

/**
 * The `docs/CONTRACTS.md §17` field allowlist in the closed, executable form that crash reports
 * use (`§20.3.1`, `D-243`).
 *
 * `§17` admits field values that are stable codes, enum names, `cycleId`, counts, durations and
 * booleans, and never raw user data. A value cannot be classified by its text — an exact odometer
 * reading and an attempt count are both digits — so the gate is the key: each key below names one
 * `§17` value kind, and [filter] drops every other key before a provider sees it.
 *
 * The set is closed. A count, duration or flag key is added by the story that first records one,
 * together with its `§20.3.1` amendment and a test.
 */
object CrashFieldAllowlist {
    /** `AppError.code` of the originating failure. */
    const val CODE = "code"

    val KEYS: Set<String> =
        setOf(
            CODE,
            "cycleId",
            "entityType",
            "syncState",
            "syncTrigger",
            "consumptionInvalidReason",
            "confirmation",
        )

    fun filter(fields: Map<String, String>): Map<String, String> = fields.filterKeys { key -> key in KEYS }
}
