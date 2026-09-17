package com.ruizurraca.carapp.buildlogic.contract

import java.io.File

/** Contract for the single canonical Gradle daemon JVM argument definition. */
internal class GradleJvmContract private constructor(
    private val gradleProperties: String,
    private val workflow: String,
) {
    constructor(repoRoot: File) : this(
        gradleProperties = repoRoot.resolve("gradle.properties").readText(),
        workflow = repoRoot.resolve(".github/workflows/ci.yml").readText(),
    )

    constructor(gradleProperties: String, workflow: String, fixture: Boolean = true) : this(gradleProperties, workflow) {
        check(fixture) { "The fixture marker prevents constructor signature ambiguity" }
    }

    fun validate(): AssertionResult {
        val definitions = JVM_ARGS.findAll(gradleProperties).count()
        val workflowOverride = workflow.contains("org.gradle.jvmargs") || workflow.contains("GRADLE_OPTS")
        val detail = buildList {
            if (definitions != 1) add("gradle.properties contains $definitions org.gradle.jvmargs definitions")
            if (workflowOverride) add("ci.yml redefines org.gradle.jvmargs through an environment override")
        }
        return if (detail.isEmpty()) {
            AssertionResult(33, "Gradle daemon JVM arguments have one canonical source", AssertionResult.Status.PASS)
        } else {
            AssertionResult(33, "Gradle daemon JVM arguments have one canonical source", AssertionResult.Status.FAIL, detail.joinToString())
        }
    }

    private companion object {
        val JVM_ARGS = Regex("(?m)^\\s*org\\.gradle\\.jvmargs\\s*=")
    }
}
