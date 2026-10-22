package com.ruizurraca.carapp.buildlogic.contract

import java.io.File

/** Contract for the `carAppUITests` shard manifest and its CI jobs (`D-230`, ADR-0231). */
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

    fun validate(): AssertionResult = AssertionResult(
        45,
        "every iOS UI test shard names existing tests and runs in exactly one ios-ui-tests job",
        AssertionResult.Status.PENDING,
        "implemented in the D-230 GREEN commit (E4-B1)",
    )
}
