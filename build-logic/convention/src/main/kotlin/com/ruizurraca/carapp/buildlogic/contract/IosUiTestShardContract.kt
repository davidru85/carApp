package com.ruizurraca.carapp.buildlogic.contract

import java.io.File

/**
 * Contract for the `carAppUITests` shard manifest and its CI jobs (`D-230`, ADR-0231).
 *
 * The shards are complete by construction: a class entry runs every method another shard does not
 * name, and the implicit `remainder` shard runs every test no entry names. That guarantee holds only
 * while every entry names a real test, each identifier is assigned once, every shard runs in exactly
 * one `ios-ui-tests-<shard>` job, and no job selects UI tests around the manifest. A stale entry
 * silently runs nothing, and a shard without a job silently drops tests the remainder skips.
 */
internal class IosUiTestShardContract private constructor(
    private val workflow: String,
    private val manifest: String,
    private val testSources: Map<String, String>,
) {
    constructor(repoRoot: File) : this(
        repoRoot.resolve(".github/workflows/ci.yml").readText(),
        repoRoot.resolve("scripts/ios-ui-test-shards.txt").readText(),
        repoRoot.resolve("iosApp/UITests")
            .listFiles { file -> file.extension == "swift" }
            .orEmpty()
            .associate { it.name to it.readText() },
    )

    constructor(workflow: String, manifest: String, testSources: Map<String, String>, fixture: Boolean = true) :
        this(workflow, manifest, testSources) {
        check(fixture) { "The fixture marker prevents constructor signature ambiguity" }
    }

    fun validate(): AssertionResult {
        val entries = manifestEntries()
        val mismatches = entries.flatMap { it.problems } +
            entryMismatches(entries.mapNotNull { it.entry }) +
            jobMismatches(entries.mapNotNull { it.entry }.map(Entry::shard).toSet() + REMAINDER) +
            directSelections()
        return if (mismatches.isEmpty()) {
            val shards = entries.mapNotNull { it.entry?.shard }.toSet().size + 1
            AssertionResult(ID, NAME, AssertionResult.Status.PASS, "$shards shards")
        } else {
            AssertionResult(ID, NAME, AssertionResult.Status.FAIL, mismatches.joinToString())
        }
    }

    private fun manifestEntries(): List<ParsedLine> =
        manifest.lines().mapIndexedNotNull { index, raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@mapIndexedNotNull null
            val match = ENTRY.matchEntire(line)
            when {
                match == null ->
                    ParsedLine(null, listOf("manifest line ${index + 1} is not '<shard> <TestClass>[/<testMethod>]'"))
                match.groupValues[1] == REMAINDER ->
                    ParsedLine(null, listOf("manifest line ${index + 1} lists the implicit $REMAINDER shard"))
                else -> ParsedLine(Entry(match.groupValues[1], match.groupValues[2], match.groupValues[3].ifEmpty { null }))
            }
        }

    private fun entryMismatches(entries: List<Entry>): List<String> {
        val classes = testClasses()
        val missing = entries.mapNotNull { entry ->
            val methods = classes[entry.testClass]
            when {
                methods == null -> "${entry.identifier} names no XCTestCase class in iosApp/UITests"
                entry.method != null && entry.method !in methods -> "${entry.identifier} is not a test method of ${entry.testClass}"
                else -> null
            }
        }
        val duplicated = entries.groupingBy(Entry::identifier).eachCount().filterValues { it > 1 }.keys
            .map { "$it is assigned more than once" }
        return missing + duplicated
    }

    /** Test classes of the UI test sources, each with the `test` methods declared after it. */
    private fun testClasses(): Map<String, Set<String>> =
        testSources.values.flatMap { source ->
            val declarations = TEST_CLASS.findAll(source).toList()
            declarations.mapIndexed { position, declaration ->
                val end = declarations.getOrNull(position + 1)?.range?.first ?: source.length
                val body = source.substring(declaration.range.last, end)
                declaration.groupValues[1] to TEST_METHOD.findAll(body).map { it.groupValues[1] }.toSet()
            }
        }.toMap()

    private fun jobMismatches(shards: Set<String>): List<String> {
        val invocations = jobs().flatMap { job -> job.shards.map { shard -> shard to job } }
        val unknown = invocations.filter { (shard, _) -> shard !in shards }
            .map { (shard, job) -> "job ${job.id} runs shard $shard, which the manifest does not define" }
        val perShard = shards.sorted().mapNotNull { shard ->
            val running = invocations.filter { it.first == shard }.map { it.second }
            val expected = "$JOB_PREFIX$shard"
            when {
                running.size != 1 -> "shard $shard runs in ${running.size} jobs, expected exactly one"
                running.single().id != expected || running.single().name != expected ->
                    "shard $shard runs in job ${running.single().id} named ${running.single().name}, expected $expected"
                else -> null
            }
        }
        return unknown + perShard
    }

    private fun directSelections(): List<String> =
        jobs().filter { job -> job.lines.any { DIRECT_SELECTION.containsMatchIn(it) } }
            .map { "job ${it.id} selects carAppUITests outside the shard manifest" }

    private fun jobs(): List<Job> {
        val lines = workflow.substringAfter("\njobs:\n").lines()
        val starts = lines.mapIndexedNotNull { index, line -> JOB_START.matchEntire(line)?.let { index to it.groupValues[1] } }
        return starts.mapIndexed { position, (start, id) ->
            val block = lines.subList(start, starts.getOrNull(position + 1)?.first ?: lines.size)
            Job(
                id = id,
                name = block.firstNotNullOfOrNull { JOB_NAME.find(it)?.groupValues?.get(1)?.trim() },
                shards = block.flatMap { line -> SHARD_RUN.findAll(line).map { it.groupValues[1] }.toList() },
                lines = block,
            )
        }
    }

    private data class Entry(val shard: String, val testClass: String, val method: String?) {
        val identifier: String = if (method == null) testClass else "$testClass/$method"
    }

    private data class ParsedLine(val entry: Entry?, val problems: List<String> = emptyList())

    private data class Job(val id: String, val name: String?, val shards: List<String>, val lines: List<String>)

    private companion object {
        const val ID = 45
        const val NAME = "every iOS UI test shard names existing tests and runs in exactly one ios-ui-tests job"
        const val REMAINDER = "remainder"
        const val JOB_PREFIX = "ios-ui-tests-"
        val ENTRY = Regex("^([a-z0-9][a-z0-9-]*)\\s+([A-Za-z_][A-Za-z0-9_]*)(?:/(test[A-Za-z0-9_]*))?$")
        val TEST_CLASS = Regex("\\bclass\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*:\\s*XCTestCase\\b")
        val TEST_METHOD = Regex("\\bfunc\\s+(test[A-Za-z0-9_]*)\\s*\\(")
        val JOB_START = Regex("^  ([A-Za-z0-9_-]+):$")
        val JOB_NAME = Regex("^\\s{4}name: (.+)$")
        val SHARD_RUN = Regex("scripts/ios-ui-test-shard\\.sh\\s+([A-Za-z0-9_-]+)")
        val DIRECT_SELECTION = Regex("-(?:only|skip)-testing:carAppUITests\\b")
    }
}
