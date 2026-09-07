package com.ruizurraca.carapp

/**
 * Maps a `D-62` reminder index to the notice body shown on Android. Every body states the benefit
 * of permanent sign-in and the device-bound 30-day cleanup risk of `docs/CONTRACTS.md §11.2`; the
 * later ones state how little time is left.
 */
@Suppress("UnusedParameter", "FunctionOnlyReturningConstant")
internal fun anonymousReminderBodyResource(index: Int): Int {
    // RED: declared without behaviour so the reminder copy test compiles and executes.
    return R.string.error_unexpected
}
