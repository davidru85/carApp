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
        ).forEach(::createProject)
    }

    fun createProject(path: String) {
        File(root, path.removePrefix(":").replace(':', File.separatorChar)).mkdirs()
    }

    fun runProjects(): String =
        GradleRunner.create()
            .withProjectDir(root)
            .withArguments("projects", "--console=plain")
            .build()
            .output
}
