package com.ruizurraca.carapp.buildlogic.contract

import java.io.File

/** RED stub of `docs/CONTRACTS.md §18` assertion 44 (`E4-02`); the GREEN commit implements the rule. */
internal class HostLiteralTextContract internal constructor(
    private val inputs: HostLocalizationInputs,
) {
    constructor(repoRoot: File) : this(HostLocalizationInputs.from(repoRoot))

    fun validate(): AssertionResult = AssertionResult(ID, NAME, AssertionResult.Status.PASS)

    companion object {
        const val ID = 44
        const val NAME = "no host UI source renders literal text outside the Debug-only allowlist"
        val IOS_DEBUG_ONLY_SOURCES: Set<String> = setOf("iosApp/DiagnosticsView.swift")
        val ANDROID_DEBUG_ONLY_SOURCES: Set<String> = emptySet()
    }
}
