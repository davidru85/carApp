package com.ruizurraca.carapp.buildlogic.contract

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class NativeTestExemptionContractTest {
    private val repositoryRoot = File(checkNotNull(System.getProperty("carapp.repoRoot")))
    private val workflow = repositoryRoot.resolve(".github/workflows/ci.yml").readText()
    private val dependencyGraph = mapOf(
        ":integration:firebase-auth" to emptySet(),
        ":integration:firebase-firestore" to emptySet(),
        ":wiring:firebase" to
            setOf(
                ":integration:firebase-auth",
                ":integration:firebase-firestore",
            ),
        ":composition:ios" to setOf(":wiring:firebase"),
        ":shared" to emptySet(),
    )
    private val nativeTestProjects = dependencyGraph.keys

    @Test
    fun expectedSetIsDerivedFromTheTransitiveDependencyGraph() {
        assertEquals(
            setOf(
                ":composition:ios",
                ":integration:firebase-auth",
                ":integration:firebase-firestore",
                ":wiring:firebase",
            ),
            NativeTestExemptionContract.deriveExpectedModules(
                dependencyGraph = dependencyGraph,
                nativeTestProjects = nativeTestProjects,
            ),
        )
    }

    @Test
    fun aFutureTransitiveConsumerQualifiesWithoutChangingTheRule() {
        val expandedGraph = dependencyGraph + (":composition:watch" to setOf(":composition:ios"))

        assertEquals(
            nativeTestProjects + ":composition:watch" - ":shared",
            NativeTestExemptionContract.deriveExpectedModules(
                dependencyGraph = expandedGraph,
                nativeTestProjects = expandedGraph.keys,
            ),
        )
    }

    @Test
    fun sharedTestsDeclareTheCurrentGraphResolution() {
        val result = NativeTestExemptionContract.validate(
            workflow = workflow,
            dependencyGraph = dependencyGraph,
            nativeTestProjects = nativeTestProjects,
        )

        assertEquals(AssertionResult.Status.PASS, result.status, result.detail)
    }

    @Test
    fun omittingAQualifyingModuleIsRejected() {
        val mutated = workflow.replace(
            "-x :wiring:firebase:iosSimulatorArm64Test",
            "",
        )

        assertEquals(
            AssertionResult.Status.FAIL,
            NativeTestExemptionContract.validate(
                workflow = mutated,
                dependencyGraph = dependencyGraph,
                nativeTestProjects = nativeTestProjects,
            ).status,
        )
    }

    @Test
    fun declaringAModuleThatDoesNotQualifyIsRejected() {
        val mutated = workflow.replace(
            "-x :composition:ios:iosSimulatorArm64Test",
            "-x :composition:ios:iosSimulatorArm64Test " +
                "-x :shared:iosSimulatorArm64Test",
        )

        assertEquals(
            AssertionResult.Status.FAIL,
            NativeTestExemptionContract.validate(
                workflow = mutated,
                dependencyGraph = dependencyGraph,
                nativeTestProjects = nativeTestProjects,
            ).status,
        )
    }
}
