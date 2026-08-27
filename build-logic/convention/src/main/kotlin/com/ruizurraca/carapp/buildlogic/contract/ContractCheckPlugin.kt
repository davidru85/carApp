package com.ruizurraca.carapp.buildlogic.contract

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.register

/** Registers `contractCheck` on the root project (`docs/CONTRACTS.md §18`, `E0-05`). */
class ContractCheckPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        require(target == target.rootProject) {
            "carapp.contract applies to the root project only; it inspects the repository documents."
        }
        val contractCheck = target.tasks.register<ContractCheckTask>("contractCheck") {
            group = "verification"
            description = "Asserts the repository invariants of docs/CONTRACTS.md §18."
            repoRoot = target.rootProject.projectDir
            outputs.upToDateWhen { false }
        }
        target.gradle.projectsEvaluated {
            contractCheck.configure {
                nativeTestDependencyGraph =
                    target.subprojects.mapNotNull { project ->
                        project.configurations
                            .findByName(NATIVE_TEST_CLASSPATH)
                            ?.let { configuration ->
                                project.path to
                                    configuration.hierarchy
                                        .flatMap { inherited ->
                                                inherited.dependencies
                                                    .withType(ProjectDependency::class.java)
                                                .map(ProjectDependency::getPath)
                                        }.toSet()
                            }
                    }.toMap()
            }
        }
    }

    private companion object {
        const val NATIVE_TEST_CLASSPATH = "iosSimulatorArm64TestCompileKlibraries"
    }
}

abstract class ContractCheckTask : DefaultTask() {
    @get:Internal
    internal var repoRoot: File? = null

    @get:Internal
    internal var nativeTestDependencyGraph: Map<String, Set<String>> = emptyMap()

    @TaskAction
    fun check() {
        val root = repoRoot ?: throw GradleException("Repository root was not set")
        val results = ContractCheck(root, nativeTestDependencyGraph).runAll()

        results.forEach { result ->
            val line = "  [${result.status}] ${result.id}. ${result.name}" +
                if (result.detail.isBlank()) "" else " — ${result.detail}"
            when (result.status) {
                AssertionResult.Status.PASS -> logger.lifecycle(line)
                AssertionResult.Status.PENDING -> logger.warn(line)
                AssertionResult.Status.FAIL -> logger.error(line)
            }
        }

        val pending = results.count { it.status == AssertionResult.Status.PENDING }
        if (pending > 0) {
            logger.warn(
                "contractCheck: $pending assertion(s) cannot run yet. They are reported rather than " +
                    "skipped, because a check that silently skips reads as coverage that is not there.",
            )
        }

        val failures = results.filter { it.status == AssertionResult.Status.FAIL }
        if (failures.isNotEmpty()) {
            throw GradleException(
                "contractCheck failed ${failures.size} assertion(s) of docs/CONTRACTS.md §18:\n" +
                    failures.joinToString("\n") { "  - ${it.id}. ${it.name}: ${it.detail}" },
            )
        }
    }
}
