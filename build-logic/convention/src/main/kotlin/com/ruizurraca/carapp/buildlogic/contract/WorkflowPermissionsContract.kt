package com.ruizurraca.carapp.buildlogic.contract

import java.io.File

/** Contract for least-privilege GitHub Actions permissions. */
internal class WorkflowPermissionsContract private constructor(
    private val workflow: String,
) {
    constructor(repoRoot: File) : this(repoRoot.resolve(".github/workflows/ci.yml").readText())

    constructor(workflow: String, fixture: Boolean = true) : this(workflow) {
        check(fixture) { "The fixture marker prevents constructor signature ambiguity" }
    }

    fun validate(): List<AssertionResult> = listOf(workflowPermissions(), jobPermissions())

    private fun workflowPermissions(): AssertionResult {
        val entries = topLevelPermissions().mapNotNull { line ->
            TOP_PERMISSION.matchEntire(line)?.let { it.groupValues[1] to it.groupValues[2] }
        }.toMap()
        val valid = entries == mapOf("contents" to "read")
        return if (valid) {
            AssertionResult(31, "workflow permissions are read-only repository contents", AssertionResult.Status.PASS)
        } else {
            AssertionResult(31, "workflow permissions are read-only repository contents", AssertionResult.Status.FAIL, entries.toString())
        }
    }

    private fun jobPermissions(): AssertionResult {
        val violations = jobsWithPermissions().flatMap { (job, entries) ->
            val allowed = if (job == "contract-check") mapOf("contents" to "read", "id-token" to "write") else emptyMap()
            if (entries == allowed) emptyList() else listOf("$job declares $entries")
        }
        return if (violations.isEmpty()) {
            AssertionResult(32, "only contract-check has the minimum additional OIDC permission", AssertionResult.Status.PASS)
        } else {
            AssertionResult(32, "only contract-check has the minimum additional OIDC permission", AssertionResult.Status.FAIL, violations.joinToString())
        }
    }

    private fun topLevelPermissions(): List<String> {
        val lines = workflow.lines()
        val start = lines.indexOfFirst { it == "permissions:" }
        if (start < 0) return emptyList()
        val end = lines.drop(start + 1).indexOfFirst { it.isNotEmpty() && !it.startsWith(" ") }
        return lines.subList(start + 1, if (end < 0) lines.size else start + 1 + end)
    }

    private fun jobsWithPermissions(): List<Pair<String, Map<String, String>>> {
        val lines = workflow.lines()
        val starts = lines.mapIndexedNotNull { index, line -> JOB_START.matchEntire(line)?.let { index to it.groupValues[1] } }
        return starts.mapIndexedNotNull { position, (start, id) ->
            val end = starts.getOrNull(position + 1)?.first ?: lines.size
            val block = lines.subList(start, end)
            val permissionIndex = block.indexOfFirst { it == "    permissions:" }
            if (permissionIndex < 0) null else {
                val tail = block.drop(permissionIndex + 1).takeWhile { it.startsWith("      ") }
                id to PERMISSION.findAll(tail.joinToString("\n")).associate { it.groupValues[1] to it.groupValues[2] }
            }
        }
    }

    private companion object {
        val JOB_START = Regex("^  ([A-Za-z0-9_-]+):$")
        val TOP_PERMISSION = Regex("^  (contents|id-token):\\s*(read|write|none)\\s*$")
        val PERMISSION = Regex("^\\s+(contents|id-token):\\s*(read|write|none)\\s*$", RegexOption.MULTILINE)
    }
}
