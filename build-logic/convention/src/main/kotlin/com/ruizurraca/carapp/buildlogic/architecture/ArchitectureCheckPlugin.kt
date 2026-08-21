package com.ruizurraca.carapp.buildlogic.architecture

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.tasks.Internal
import org.gradle.kotlin.dsl.register
import org.gradle.api.tasks.TaskAction

/**
 * Registers the `architectureCheck` task on the root project (`docs/BACKLOG.md` `E0-04`).
 *
 * The rules themselves live in [ArchitectureChecker] as pure functions over plain data. This class
 * only collects that data from the Gradle model, which is what keeps every rule testable without
 * creating the offending module.
 */
class ArchitectureCheckPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        require(target == target.rootProject) {
            "carapp.architecture applies to the root project only; it inspects the whole module graph."
        }

        val task = target.tasks.register<ArchitectureCheckTask>("architectureCheck") {
            group = "verification"
            description = "Checks the module graph against the dependency rules of docs/TECHNICAL_PLAN.md §4."
            outputs.upToDateWhen { false }
        }

        target.gradle.projectsEvaluated {
            val modules = target.rootProject.subprojects.map { describe(it) }
            task.configure {
                this.modules = modules
                this.technicalPlan = target.rootProject.file("docs/TECHNICAL_PLAN.md")
                this.versionCatalog = target.rootProject.file("gradle/libs.versions.toml")
            }
        }
    }

    private fun describe(project: Project): ModuleUnderCheck {
        val projectDependencies = mutableSetOf<String>()
        val externalDependencies = mutableSetOf<String>()

        project.configurations.forEach { configuration ->
            configuration.dependencies.forEach { dependency ->
                when (dependency) {
                    is ProjectDependency -> projectDependencies += dependency.path
                    is ExternalModuleDependency -> externalDependencies += "${dependency.group}:${dependency.name}"
                    else -> Unit
                }
            }
        }

        val sourceLines = mutableListOf<SourceLine>()
        val imports = mutableSetOf<String>()
        val sourceRoot = File(project.projectDir, "src")
        if (sourceRoot.isDirectory) {
            sourceRoot.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                // Test sources are excluded: a test may legitimately name a forbidden type in
                // order to assert that it is forbidden.
                .filterNot { it.path.contains("Test/") || it.path.contains("test/") }
                .forEach { file ->
                    file.readLines().forEachIndexed { index, text ->
                        val stripped = stripComment(text)
                        if (stripped.isNotBlank()) {
                            sourceLines += SourceLine(file.name, index + 1, stripped)
                        }
                        if (stripped.trimStart().startsWith("import ")) {
                            imports += stripped.trim().removePrefix("import ").trim()
                        }
                    }
                }
        }

        return ModuleUnderCheck(
            path = project.path,
            projectDependencies = projectDependencies,
            externalDependencies = externalDependencies,
            imports = imports,
            sourceLines = sourceLines,
            appliedPluginIds = SKIE_PLUGIN_IDS.filter { project.pluginManager.hasPlugin(it) }.toSet(),
        )
    }

    /** Comments are stripped so that prose naming a forbidden type does not trip its own rule. */
    private fun stripComment(line: String): String {
        val trimmed = line.trim()
        if (trimmed.startsWith("*") || trimmed.startsWith("//") || trimmed.startsWith("/*")) return ""
        return line.substringBefore("//")
    }

    private companion object {
        val SKIE_PLUGIN_IDS = listOf("co.touchlab.skie")
    }
}

abstract class ArchitectureCheckTask : DefaultTask() {
    @get:Internal
    internal var modules: List<ModuleUnderCheck> = emptyList()

    @get:Internal
    internal var technicalPlan: File? = null

    @get:Internal
    internal var versionCatalog: File? = null

    @TaskAction
    fun check() {
        val plan = technicalPlan
            ?: throw GradleException("docs/TECHNICAL_PLAN.md was not located; the rules are generated from it.")
        val rules = DependencyRuleTableParser.parse(plan.readText())
        logger.lifecycle("architectureCheck: ${rules.size} rules from docs/TECHNICAL_PLAN.md §4, ${modules.size} modules.")

        val violations = ArchitectureChecker.check(modules, rules) +
            (versionCatalog?.let { ArchitectureChecker.checkImageLoading(it.readText()) } ?: emptyList())

        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Architecture check failed with ${violations.size} violation(s):")
                    violations.forEach { appendLine("  - $it") }
                    appendLine()
                    appendLine("The rules are generated from docs/TECHNICAL_PLAN.md §4 and docs/CONTRACTS.md.")
                },
            )
        }
    }
}
