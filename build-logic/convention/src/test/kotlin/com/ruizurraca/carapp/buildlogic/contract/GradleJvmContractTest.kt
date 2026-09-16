package com.ruizurraca.carapp.buildlogic.contract

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class GradleJvmContractTest {
    private val repositoryRoot = File(checkNotNull(System.getProperty("carapp.repoRoot")))
    private val properties = repositoryRoot.resolve("gradle.properties").readText()
    private val workflow = repositoryRoot.resolve(".github/workflows/ci.yml").readText()

    @Test
    fun repositoryUsesOneCanonicalDefinition() {
        assertEquals(AssertionResult.Status.PASS, GradleJvmContract(properties, workflow).validate().status)
    }

    @Test
    fun duplicatePropertyDefinitionFails() {
        val mutated = "$properties\norg.gradle.jvmargs = -Xmx2g"
        assertEquals(AssertionResult.Status.FAIL, GradleJvmContract(mutated, workflow).validate().status)
    }

    @Test
    fun workflowOverrideFails() {
        val mutated = "$workflow\nenv:\n  GRADLE_OPTS: -Dorg.gradle.jvmargs=-Xmx2g\n"
        assertEquals(AssertionResult.Status.FAIL, GradleJvmContract(properties, mutated).validate().status)
    }
}
