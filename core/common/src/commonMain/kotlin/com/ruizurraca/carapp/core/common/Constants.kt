package com.ruizurraca.carapp.core.common

/**
 * The named constants of `docs/CONTRACTS.md §20.0.1`.
 *
 * Writing any of these literals inline instead of referencing the constant is a contract
 * violation. `LOCAL_OWNER` is the one exception to this file: it lives with `OwnerId` in
 * `:core:model`, because `:core:common` depends on `:core:model` and never the reverse.
 */

/** Highest `schemaVersion` this client applies (`§9.5`). */
const val CLIENT_MAX_SCHEMA_VERSION: Int = 1

/** `attemptCount` ceiling (`§9.7`). */
const val MAX_RETRYABLE_ATTEMPTS: Int = 10

/** Per-vehicle fuel entry load ceiling (`§12`). */
const val MAX_ENTRIES_IN_MEMORY: Int = 5_000

/** Android `enqueueUniqueWork` name (`§9.1`). */
const val SYNC_WORK: String = "carapp-sync"

/** `WhileSubscribed` timeout (`§14`). */
const val STATE_HOLDER_TIMEOUT_MS: Long = 5_000L

/** Five minutes (`§9.8`). */
const val FOREGROUND_RESUME_THRESHOLD_MS: Long = 300_000L

/** Five minutes (`§11.5`). */
const val FRESH_LOGIN_THRESHOLD_MS: Long = 300_000L

/**
 * Every supported MVP currency has exactly two decimal minor units, factor 100 (`§2`).
 *
 * Each code MUST be verified as two-decimal by the MVP platform locale APIs on Android and iOS. If
 * a runtime reports a different minor-unit factor for any supported code, validation falls back to
 * `EUR` rather than accepting a different factor. Extending this table is a backlog story, not an
 * agent decision.
 */
val SUPPORTED_CURRENCY_CODES: Set<String> = setOf(
    "ARS",
    "AUD",
    "BRL",
    "CAD",
    "CHF",
    "COP",
    "CZK",
    "DKK",
    "EUR",
    "GBP",
    "HUF",
    "MAD",
    "MXN",
    "NOK",
    "NZD",
    "PEN",
    "PLN",
    "RON",
    "SEK",
    "USD",
    "UYU",
)

/** Failures that MUST NOT consume the poison budget (`§9.7`). */
val CONNECTIVITY_ERROR_CODES: Set<String> = setOf(
    "REMOTE.UNAVAILABLE",
    "REMOTE.DEADLINE_EXCEEDED",
)
