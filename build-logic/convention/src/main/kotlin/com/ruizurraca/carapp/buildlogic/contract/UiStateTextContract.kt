package com.ruizurraca.carapp.buildlogic.contract

import java.io.File

/** RED stub of `docs/CONTRACTS.md §18` assertion 43 (`E4-02`); the GREEN commit implements the rule. */
internal class UiStateTextContract internal constructor(
    private val inputs: Inputs,
) {
    constructor(repoRoot: File) : this(Inputs.from(repoRoot))

    internal data class Inputs(
        val contract: String,
        val sources: Map<String, String>,
    ) {
        companion object {
            fun from(repoRoot: File): Inputs =
                Inputs(
                    contract = repoRoot.resolve(HostLocalizationInputs.CONTRACTS).readText(),
                    sources = UiMessageCodeInventory.producerSources(repoRoot),
                )
        }
    }

    fun validate(): AssertionResult = AssertionResult(ID, NAME, AssertionResult.Status.PASS)

    companion object {
        const val ID = 43
        const val NAME = "UiState text fields are limited to the §14.2 allowlist"
        const val ALLOWLIST_HEADING = "### 14.2 UiState text-field allowlist"
        val KINDS = listOf("Identifier", "Currency code", "User-entered", "Programmatic code")
    }
}
