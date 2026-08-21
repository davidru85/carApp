package com.ruizurraca.carapp

/**
 * Minimal shared entry point used by the E0-01 walking skeleton.
 *
 * The product token `carApp` is intentionally hard-coded here so both host apps
 * can prove they are consuming `commonMain` rather than a per-platform string.
 * Per docs/CONTRACTS.md §15.3 and D-2, the public API of `:shared` carries no
 * value class, no project-owned type parameter and no default argument.
 */
class Greeting {
    // Returning a constant is the entire point: this is the E0-01 placeholder that proves both
    // hosts consume commonMain. It is replaced by real shared code in E0-07.
    @Suppress("FunctionOnlyReturningConstant")
    fun greet(): String = "carApp"

    fun greet(platform: String): String = "carApp on $platform"
}
