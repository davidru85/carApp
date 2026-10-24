package com.ruizurraca.carapp.buildlogic.contract

/**
 * `E4-04`: a UI-test hook is a read of the process launch environment, and a Release build MUST NOT
 * contain one. RED stub: GREEN implements the rule.
 */
internal object SwiftDebugOnlyHookRule {
    @Suppress("UnusedParameter")
    fun violations(sources: Map<String, String>): List<String> = emptyList()
}
