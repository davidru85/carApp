package com.ruizurraca.carapp.buildlogic

import org.gradle.testkit.runner.GradleRunner
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class ProviderSettingsFunctionalTest {
    @Test
    fun providerRegistryIncludesExactlyExistingModules() = withSettingsFixture { fixture ->
        fixture.createProject(":integration:firebase-auth")
        fixture.createProject(":wiring:firebase")

        val output = fixture.runProjects()

        assertContains(output, "Project ':integration:firebase-auth'")
        assertContains(output, "Project ':wiring:firebase'")
        assertFalse(output.contains("Project ':integration:firebase-firestore'"))
        assertFalse(output.contains("Project ':integration:firebase-analytics'"))
        assertFalse(output.contains("Project ':integration:firebase-crashlytics'"))
    }

    @Test
    fun providerExclusionOmitsEveryExistingProviderModule() = withSettingsFixture { fixture ->
        val providerProjects = listOf(
            ":integration:firebase-auth",
            ":integration:firebase-firestore",
            ":integration:firebase-analytics",
            ":integration:firebase-crashlytics",
            ":wiring:firebase",
        )
        providerProjects.forEach(fixture::createProject)

        val output = fixture.runProjects("-Pcarapp.excludeFirebaseProviders=true")

        providerProjects.forEach { path ->
            assertFalse(output.contains("Project '$path'"), "$path must be absent in provider-free mode")
        }
    }

    private fun withSettingsFixture(block: (SettingsFixture) -> Unit) {
        val directory = Files.createTempDirectory("carapp-provider-settings").toFile()
        try {
            block(SettingsFixture(directory))
        } finally {
            directory.deleteRecursively()
        }
    }
}

private class SettingsFixture(private val root: File) {
    private val repositoryRoot = File(checkNotNull(System.getProperty("carapp.repoRoot")))

    init {
        File(repositoryRoot, "settings.gradle.kts").copyTo(File(root, "settings.gradle.kts"))
        File(root, "build-logic").mkdirs()
        File(root, "build-logic/settings.gradle.kts").writeText("rootProject.name = \"build-logic\"\n")
        File(root, "build-logic/build.gradle.kts").writeText("")

        listOf(
            ":androidApp",
            ":shared",
            ":core:model",
            ":core:common",
            ":core:analytics",
            ":core:crash",
            ":core:testing",
            ":core:database",
            ":core:auth",
            ":core:sync",
            ":feature:vehicle",
            ":feature:fuel",
            ":feature:session",
        ).forEach(::createProject)
    }

    fun createProject(path: String) {
        File(root, path.removePrefix(":").replace(':', File.separatorChar)).mkdirs()
    }

    fun runProjects(vararg options: String): String =
        GradleRunner.create()
            .withProjectDir(root)
            .withArguments(options.toList() + listOf("projects", "--console=plain"))
            .build()
            .output
}
