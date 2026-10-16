package com.ruizurraca.carapp.buildlogic.contract

import java.io.File

/** RED stub of the `UiMessage` code inventory (`E4-02`); the GREEN commit parses the sources. */
internal object UiMessageCodeInventory {
    const val APP_ERROR_PATH = "core/common/src/commonMain/kotlin/com/ruizurraca/carapp/core/common/AppError.kt"
    const val CONFIRMATION_PREFIX = "CONFIRMATION."

    fun appErrorCodes(source: String): List<String> = emptyList()

    fun confirmationCodes(source: String): List<String> = emptyList()

    fun infoCodes(sources: Map<String, String>): List<String> = emptyList()

    fun producerSources(repoRoot: File): Map<String, String> = emptyMap()

    fun relativePath(repoRoot: File, file: File): String = file.path
}
