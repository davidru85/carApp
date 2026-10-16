package com.ruizurraca.carapp.buildlogic.contract

import java.io.File

/** RED stub of `docs/CONTRACTS.md §18` assertion 41 (`E4-02`); the GREEN commit implements the rule. */
internal class UiMessageMappingContract internal constructor(
    private val inputs: HostLocalizationInputs,
) {
    constructor(repoRoot: File) : this(HostLocalizationInputs.from(repoRoot))

    fun validate(): AssertionResult = AssertionResult(ID, NAME, AssertionResult.Status.PASS)

    companion object {
        const val ID = 41
        const val NAME = "every UiMessage code has an explicit mapping on both hosts and in §14.1"
        const val CONTRACT_TABLE = "docs/CONTRACTS.md §14.1"
        const val TABLE_HEADING = "### 14.1 UiMessage code-to-copy table"
        const val GENERIC_KEY = "error_unexpected"
    }
}
