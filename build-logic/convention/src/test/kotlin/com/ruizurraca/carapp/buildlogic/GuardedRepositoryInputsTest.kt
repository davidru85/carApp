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
 * This test fails when a guard starts reading a repository file that the task does not declare.
 */
class GuardedRepositoryInputsTest {
    private val repositoryRoot = File(checkNotNull(System.getProperty("carapp.repoRoot")))

    @Test
    fun everyRepositoryFileReadByAGuardIsADeclaredTaskInput() {
        val buildScript = repositoryRoot.resolve(CONVENTION_BUILD_SCRIPT).readText()
        val declaredPatterns = declaredInputPatterns(buildScript)
        assertTrue(
            declaredPatterns.isNotEmpty(),
            "$CONVENTION_BUILD_SCRIPT declares no guarded repository inputs for the test task.",
        )

        val undeclared =
            repositoryFilesReadByGuards()
                .filterNot { path -> declaredPatterns.any { pattern -> pattern.matches(path) } }
                .sorted()

        assertTrue(
            undeclared.isEmpty(),
            "These repository files are read by a guard but are not declared as task inputs in " +
                "$CONVENTION_BUILD_SCRIPT, so the guard can report UP-TO-DATE after they change: " +
                "$undeclared",
        )
    }

    /** Repository-relative paths a guard reads: a literal that resolves to a committed file. */
    private fun repositoryFilesReadByGuards(): Set<String> =
        repositoryRoot
            .resolve(GUARD_SOURCE_DIRECTORY)
            .walkTopDown()
            .filter { file -> file.isFile && file.extension == "kt" }
            .flatMap { file -> READ_LITERAL.findAll(file.readText()) }
            .map { match -> match.groupValues[1] }
            .filter { path -> repositoryRoot.resolve(path).isFile }
            .toSet()

    private fun declaredInputPatterns(buildScript: String): List<Regex> {
        val declaration = buildScript.substringAfter(GUARDED_INPUTS_PROPERTY, "")
        val includedPatterns = INCLUDE_LIST.find(declaration)?.groupValues?.get(1).orEmpty()
        return QUOTED_LITERAL
            .findAll(includedPatterns)
            .map { match -> antPatternToRegex(match.groupValues[1]) }
            .toList()
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

        val READ_LITERAL = Regex("""(?:\.resolve\(|File\(repositoryRoot,\s*)"([^"]+)"\)""")
        val INCLUDE_LIST = Regex("""include\(([^)]*)\)""")
        val QUOTED_LITERAL = Regex(""""([^"]+)"""")
    }
}
