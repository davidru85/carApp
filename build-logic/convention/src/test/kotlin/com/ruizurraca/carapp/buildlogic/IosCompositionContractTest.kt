package com.ruizurraca.carapp.buildlogic

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IosCompositionContractTest {
    private val repositoryRoot = File(checkNotNull(System.getProperty("carapp.repoRoot")))

    @Test
    fun compositionModuleIsTheOnlySharedFrameworkAndSkieOwner() {
        val sharedBuild = repositoryRoot.resolve("shared/build.gradle.kts").readText()
        val compositionBuild = repositoryRoot.resolve("composition/ios/build.gradle.kts").readText()
        val skiePlugin =
            repositoryRoot
                .resolve(
                    "build-logic/convention/src/main/kotlin/com/ruizurraca/carapp/buildlogic/" +
                        "SkieConventionPlugin.kt",
                ).readText()

        assertFalse(sharedBuild.contains("carapp.skie"))
        assertFalse(sharedBuild.contains("binaries.framework"))
        assertTrue(compositionBuild.contains("id(\"carapp.skie\")"))
        assertTrue(compositionBuild.contains("api(project(\":shared\"))"))
        assertTrue(compositionBuild.contains("implementation(project(\":wiring:firebase\"))"))
        assertTrue(compositionBuild.contains("export(project(\":shared\"))"))
        assertTrue(compositionBuild.contains("baseName = \"Shared\""))
        assertTrue(skiePlugin.contains("SKIE_ALLOWED_MODULE = \":composition:ios\""))
    }

    @Test
    fun swiftFactoryHasOneProductionDeclarationAndDelegatesToSharedGraphFactory() {
        val productionSources =
            repositoryRoot
                .walkTopDown()
                .filter { file ->
                    file.isFile &&
                        file.extension == "kt" &&
                        "${File.separator}src${File.separator}" in file.path &&
                        "${File.separator}build${File.separator}" !in file.path &&
                        "${File.separator}test${File.separator}" !in file.path.lowercase()
                }.toList()
        val declarations =
            productionSources.filter { file ->
                Regex("fun\\s+createSwiftAppGraph\\s*\\(").containsMatchIn(file.readText())
            }

        assertEquals(1, declarations.size)
        assertTrue(
            declarations.single().path.endsWith(
                "composition/ios/src/commonMain/kotlin/com/ruizurraca/carapp/CreateSwiftAppGraph.kt",
            ),
        )
        assertTrue(declarations.single().readText().contains("buildAppGraph(isDebugBuild, providers)"))
    }

    @Test
    fun platformHostsAndCiConsumeTheCompositionBoundary() {
        val androidBuild = repositoryRoot.resolve("androidApp/build.gradle.kts").readText()
        val xcodeProject = repositoryRoot.resolve("iosApp/project.yml").readText()
        val ci = repositoryRoot.resolve(".github/workflows/ci.yml").readText()

        assertTrue(androidBuild.contains("implementation(project(\":wiring:firebase\"))"))
        assertTrue(androidBuild.contains("carapp.excludeFirebaseProviders"))
        assertTrue(
            xcodeProject.contains(
                "../composition/ios/build/bin/iosSimulatorArm64/debugFramework/Shared.framework",
            ),
        )
        assertTrue(
            xcodeProject.contains(":composition:ios:embedAndSignAppleFrameworkForXcode"),
        )
        assertFalse(xcodeProject.contains("../shared/build/bin/iosSimulatorArm64/debugFramework"))
        assertTrue(ci.contains(":composition:ios:linkDebugFrameworkIosSimulatorArm64"))
        assertTrue(
            ci.contains(
                "composition/ios/build/bin/iosSimulatorArm64/debugFramework/" +
                    "Shared.framework/Headers/Shared.h",
            ),
        )
        assertTrue(ci.contains("shared/build/generated/objc-header/Shared.h.golden"))
    }
}
