package com.ruizurraca.carapp.buildlogic

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * These guards read committed repository files through the `carapp.repoRoot` system property. Gradle
 * cannot observe those reads, so unless the files are declared as task inputs the test task reports
 * `UP-TO-DATE` after the very configuration it guards has changed, and a broken repository looks
 * green locally while CI fails (`D-119`).
 *
 * Reads are not only path literals: guards also resolve paths from constants, compose them from
 * other constants, and walk directories, including one that walks the whole repository. A coverage
 * mechanism built on literals alone therefore cannot see what the guards actually read, so the
 * declaration covers the repository and only excludes generated output and machine-local files.
 */
class GuardedRepositoryInputsTest {
    private val repositoryRoot = File(checkNotNull(System.getProperty("carapp.repoRoot")))

    @Test
    fun theDeclaredInputsAreNotNarrowedToAnAllowlist() {
        val declaration = guardedInputsDeclaration()

        assertTrue(
            !declaration.contains("include("),
            "The guards resolve paths from constants and walk directories, so an include allowlist " +
                "cannot cover their reads. $CONVENTION_BUILD_SCRIPT must declare the repository and " +
                "exclude only generated output and machine-local files.",
        )
    }

    @Test
    fun onlyGeneratedOutputAndMachineLocalFilesAreExcluded() {
        val unapproved = excludedPatterns().filterNot { pattern -> pattern in APPROVED_EXCLUSIONS }

        assertTrue(
            unapproved.isEmpty(),
            "These exclusions are not generated output or machine-local files, so they can hide a " +
                "guarded repository file from the task inputs: $unapproved",
        )
    }

    @Test
    fun machineLocalFilesStayOutOfTheTaskInputs() {
        val missing = MACHINE_LOCAL_EXCLUSIONS.filterNot { pattern -> pattern in excludedPatterns() }

        assertTrue(
            missing.isEmpty(),
            "Machine-local files MUST NOT become cache inputs, because they differ per developer " +
                "and can carry signing configuration: $missing",
        )
    }

    @Test
    fun everyRepositoryFileAGuardReadsIsInsideTheDeclaredInputs() {
        val hidden = repositoryFilesReadByGuards().filter(::isHiddenFromInputs).sorted()

        assertTrue(
            hidden.isEmpty(),
            "These files are read by a guard but are excluded from the declared task inputs: $hidden",
        )
    }

    /**
     * Paths a guard reaches through a constant or a composed constant, which a literal scan of call
     * sites does not see. They are named here so that narrowing the declaration fails loudly.
     */
    @Test
    fun constantResolvedAndDirectoryScannedReadsAreInsideTheDeclaredInputs() {
        val hidden = INDIRECTLY_READ_PATHS.filter(::isHiddenFromInputs).sorted()

        assertTrue(hidden.isEmpty(), "Indirectly read guarded paths are excluded from the inputs: $hidden")
        INDIRECTLY_READ_PATHS.forEach { path ->
            assertTrue(
                repositoryRoot.resolve(path).exists(),
                "$path no longer exists; update the guards and this list together.",
            )
        }
    }

    /**
     * A path is hidden when an exclusion matches it and no declaration names it back. Committed
     * files that live under a generated directory, such as the Objective-C golden header, are
     * declared individually rather than by widening the exclusions. Machine-local exclusions are
     * deliberate and never count as hiding: those files MUST NOT be cache inputs.
     */
    private fun isHiddenFromInputs(path: String): Boolean {
        val declaredIndividually = individuallyDeclaredInputs().any { declared -> declared == path }
        if (declaredIndividually) return false
        return excludedPatterns()
            .filterNot { pattern -> pattern in MACHINE_LOCAL_EXCLUSIONS }
            .map(::antPatternToRegex)
            .any { pattern -> pattern.matches(path) }
    }

    private fun individuallyDeclaredInputs(): List<String> =
        repositoryRoot
            .resolve(CONVENTION_BUILD_SCRIPT)
            .readText()
            .substringAfter(INDIVIDUAL_INPUTS_PROPERTY, "")
            .substringBefore("\ntasks.test")
            .let { declaration -> QUOTED_LITERAL.findAll(declaration).map { it.groupValues[1] }.toList() }

    /** Every string literal in a guard source that resolves to a committed file. */
    private fun repositoryFilesReadByGuards(): Set<String> =
        repositoryRoot
            .resolve(GUARD_SOURCE_DIRECTORY)
            .walkTopDown()
            .filter { file -> file.isFile && file.extension == "kt" }
            .flatMap { file -> STRING_LITERAL.findAll(file.readText()) }
            .map { match -> match.groupValues[1] }
            .filter { candidate -> candidate.isNotBlank() && repositoryRoot.resolve(candidate).isFile }
            .toSet()

    private fun guardedInputsDeclaration(): String =
        repositoryRoot
            .resolve(CONVENTION_BUILD_SCRIPT)
            .readText()
            .substringAfter(GUARDED_INPUTS_PROPERTY, "")
            .substringBefore("\ntasks.test")

    private fun excludedPatterns(): List<String> {
        val declaration = guardedInputsDeclaration()
        val excludeList = EXCLUDE_LIST.find(declaration)?.groupValues?.get(1).orEmpty()
        return QUOTED_LITERAL.findAll(excludeList).map { match -> match.groupValues[1] }.toList()
    }

    /** Minimal Ant-style matching: `**` spans directories, `*` stays inside one path segment. */
    private fun antPatternToRegex(pattern: String): Regex {
        val expression =
            StringBuilder().apply {
                var index = 0
                while (index < pattern.length) {
                    when {
                        pattern.startsWith("**/", index) -> {
                            append("(?:.*/)?")
                            index += 3
                        }

                        pattern.startsWith("**", index) -> {
                            append(".*")
                            index += 2
                        }

                        pattern[index] == '*' -> {
                            append("[^/]*")
                            index += 1
                        }

                        else -> {
                            append(Regex.escape(pattern[index].toString()))
                            index += 1
                        }
                    }
                }
            }
        return Regex(expression.toString())
    }

    private companion object {
        const val CONVENTION_BUILD_SCRIPT = "build-logic/convention/build.gradle.kts"
        const val GUARD_SOURCE_DIRECTORY = "build-logic/convention/src/test/kotlin"
        const val GUARDED_INPUTS_PROPERTY = "guardedRepositoryInputs"
        const val INDIVIDUAL_INPUTS_PROPERTY = "committedGeneratedInputs"

        val STRING_LITERAL = Regex(""""([^"$\\\n]+)"""")
        val EXCLUDE_LIST = Regex("""exclude\(([^)]*)\)""")
        val QUOTED_LITERAL = Regex(""""([^"]+)"""")

        /** Generated output, tooling state and machine-local configuration. Nothing else. */
        val APPROVED_EXCLUSIONS =
            setOf(
                "**/build/**",
                "**/.gradle/**",
                "**/.git/**",
                "**/.kotlin/**",
                "**/node_modules/**",
                "**/DerivedData/**",
                "**/xcuserdata/**",
                "**/*.xcuserstate",
                "**/*.log",
                "functions/lib/**",
                "iosApp/Local.xcconfig",
                "local.properties",
            )

        /** Excluded on purpose: per-developer files that MUST NOT influence the task fingerprint. */
        val MACHINE_LOCAL_EXCLUSIONS =
            setOf(
                "iosApp/Local.xcconfig",
                "local.properties",
            )

        val INDIRECTLY_READ_PATHS =
            listOf(
                "composition/ios/src/iosMain/kotlin/com/ruizurraca/carapp/locale",
                "composition/ios/src/iosMain/kotlin/com/ruizurraca/carapp/locale/IosLocaleProvider.kt",
                "shared/src/iosTest/kotlin/com/ruizurraca/carapp/locale/IosLocaleProviderTest.kt",
                "core/common/src/commonMain/kotlin/com/ruizurraca/carapp/core/common/AppError.kt",
                "core/common/src/commonMain/kotlin/com/ruizurraca/carapp/core/common/PlatformAbstractions.kt",
                "shared/src/commonMain/kotlin/com/ruizurraca/carapp/UiModels.kt",
            )
    }
}
