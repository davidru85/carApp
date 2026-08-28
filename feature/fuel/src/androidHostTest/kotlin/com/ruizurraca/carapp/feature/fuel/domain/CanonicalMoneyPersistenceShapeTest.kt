package com.ruizurraca.carapp.feature.fuel.domain

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class CanonicalMoneyPersistenceShapeTest {
    @Test
    fun localRemoteAndOutboxSourcesContainNoSuppliedPairMarker() {
        val root = locateRepositoryRoot()
        val persistenceRoots =
            listOf(
                File(root, "core/database/src"),
                File(root, "firestore"),
                File(root, "integration/firebase-firestore/src"),
                File(root, "feature/vehicle/src/commonMain"),
            ).filter(File::exists)
        val files =
            persistenceRoots.flatMap { directory ->
                directory.walkTopDown().filter { it.isFile && it.extension in SOURCE_EXTENSIONS }.toList()
            }

        assertTrue(files.isNotEmpty(), "Found no persistence sources under $root")
        files.forEach { file ->
            val source = file.readText()
            SUPPLIED_PAIR_MARKERS.forEach { marker ->
                assertFalse(marker in source, "$marker must not be persisted by ${file.relativeTo(root)}")
            }
        }
    }

    @Test
    fun localSchemaStoresTheCanonicalTripleAsIntegerColumns() {
        val schemaPath =
            "core/database/src/commonMain/sqldelight/" +
                "com/ruizurraca/carapp/core/database/schema.sq"
        val schema = File(locateRepositoryRoot(), schemaPath).readText()

        assertTrue(Regex("""litersScaled\s+INTEGER\s+NOT\s+NULL""").containsMatchIn(schema))
        assertTrue(Regex("""pricePerLiterScaled\s+INTEGER\s+NOT\s+NULL""").containsMatchIn(schema))
        assertTrue(Regex("""totalCostMinor\s+INTEGER\s+NOT\s+NULL""").containsMatchIn(schema))
    }

    private fun locateRepositoryRoot(): File {
        var candidate: File? = File(".").absoluteFile
        while (candidate != null) {
            if (File(candidate, "settings.gradle.kts").isFile && File(candidate, "docs/CONTRACTS.md").isFile) {
                return candidate
            }
            candidate = candidate.parentFile
        }
        fail("Could not locate repository root from ${File(".").absolutePath}")
    }

    private companion object {
        val SOURCE_EXTENSIONS = setOf("kt", "sq", "rules", "json", "js", "mjs", "ts")
        val SUPPLIED_PAIR_MARKERS = setOf("moneyInputKind", "moneyInputMode")
    }
}
