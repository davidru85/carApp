package com.ruizurraca.carapp.feature.fuel.domain

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

class NoFloatingPointFuelArithmeticTest {
    @Test
    fun fuelDomainSourcesContainNoFloatingPointTypesOrConversions() {
        val sourceRoot = locateCommonMain()
        val sources = sourceRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

        assertTrue(sources.isNotEmpty(), "Found no Kotlin sources under $sourceRoot")
        val offenders =
            sources.flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    if (FORBIDDEN.containsMatchIn(stripComment(line))) {
                        "${file.name}:${index + 1}: ${line.trim()}"
                    } else {
                        null
                    }
                }
            }

        if (offenders.isNotEmpty()) {
            fail("Floating-point arithmetic is forbidden in the Fuel Entry domain:\n${offenders.joinToString("\n")}")
        }
    }

    private fun locateCommonMain(): File {
        var candidate: File? = File(".").absoluteFile
        while (candidate != null) {
            val source = File(candidate, "src/commonMain/kotlin")
            if (source.isDirectory) return source
            candidate = candidate.parentFile
        }
        fail("Could not locate src/commonMain/kotlin from ${File(".").absolutePath}")
    }

    private fun stripComment(line: String): String {
        val trimmed = line.trim()
        if (trimmed.startsWith("*") || trimmed.startsWith("//") || trimmed.startsWith("/*")) return ""
        return line.substringBefore("//")
    }

    private companion object {
        val FORBIDDEN = Regex("""\b(Float|Double|toFloat|toDouble)\b""")
    }
}
