package com.ruizurraca.carapp.buildlogic.contract

import java.io.File

/** RED stub of `docs/CONTRACTS.md §18` assertion 42 (`E4-02`); the GREEN commit implements the rule. */
internal class LocalizationKeyContract internal constructor(
    private val inputs: HostLocalizationInputs,
) {
    constructor(repoRoot: File) : this(HostLocalizationInputs.from(repoRoot))

    fun validate(): AssertionResult = AssertionResult(ID, NAME, AssertionResult.Status.PASS)

    companion object {
        const val ID = 42
        const val NAME = "every referenced localization key exists in both catalogues of its host"
    }
}
