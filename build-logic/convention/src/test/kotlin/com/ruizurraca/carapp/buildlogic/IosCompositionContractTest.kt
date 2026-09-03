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
        assertTrue(compositionBuild.contains("export(project(\":feature:vehicle\"))"))
        assertTrue(compositionBuild.contains("export(project(\":core:common\"))"))
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
                "composition/ios/src/iosMain/kotlin/com/ruizurraca/carapp/CreateSwiftAppGraph.kt",
            ),
        )
        val factory = declarations.single().readText()
        assertTrue(factory.contains("localeProvider = IosLocaleProvider()"))
        assertTrue(factory.contains("buildAppGraph(isDebugBuild, providers)"))
        assertTrue(factory.contains("NSApplicationSupportDirectory"))
    }

    @Test
    fun platformHostsAndCiConsumeTheCompositionBoundary() {
        val androidBuild = repositoryRoot.resolve("androidApp/build.gradle.kts").readText()
        val xcodeProject = repositoryRoot.resolve("iosApp/project.yml").readText()
        val ci = repositoryRoot.resolve(".github/workflows/ci.yml").readText()

        assertTrue(androidBuild.contains("implementation(project(\":wiring:firebase\"))"))
        assertTrue(androidBuild.contains("carapp.excludeFirebaseProviders"))
        assertTrue(
            xcodeProject.contains(":composition:ios:embedAndSignAppleFrameworkForXcode"),
        )
        assertTrue(xcodeProject.contains("ENABLE_USER_SCRIPT_SANDBOXING: NO"))
        assertFalse(xcodeProject.contains("build/bin/iosSimulatorArm64/debugFramework"))
        assertFalse(xcodeProject.contains("framework: ../composition"))
        assertTrue(ci.contains(":composition:ios:linkDebugFrameworkIosSimulatorArm64"))
        assertTrue(
            ci.contains(
                "composition/ios/build/bin/iosSimulatorArm64/debugFramework/" +
                    "Shared.framework/Headers/Shared.h",
            ),
        )
        assertTrue(ci.contains("shared/build/generated/objc-header/Shared.h.golden"))
    }

    @Test
    fun androidHostLocaleProviderTestsRunInCanonicalVerification() {
        val agents = repositoryRoot.resolve("AGENTS.md").readText()
        val ci = repositoryRoot.resolve(".github/workflows/ci.yml").readText()

        assertTrue(agents.contains(":androidApp:testDebugUnitTest"))
        assertTrue(ci.contains(":androidApp:testDebugUnitTest testAndroidHostTest"))
    }

    @Test
    fun iosHostLocaleProviderTestsRunInCanonicalVerification() {
        val sharedBuild = repositoryRoot.resolve("shared/build.gradle.kts").readText()
        val providerSourceDirectory = repositoryRoot.resolve(IOS_LOCALE_PROVIDER_SOURCE_DIRECTORY)
        val providerSource = repositoryRoot.resolve(IOS_LOCALE_PROVIDER_SOURCE_PATH)
        val providerTest = repositoryRoot.resolve(IOS_LOCALE_PROVIDER_TEST_PATH)
        val agents = repositoryRoot.resolve("AGENTS.md").readText()
        val ci = repositoryRoot.resolve(".github/workflows/ci.yml").readText()

        assertTrue(
            sharedBuild.contains("composition/ios/src/iosMain/kotlin/com/ruizurraca/carapp/locale"),
            "D-109 requires shared iosTest to reuse the composition-owned provider source; " +
                "update the test route and D-109 together if the source moves",
        )
        assertTrue(
            providerSource.isFile,
            "D-109 requires the composition-owned provider at $IOS_LOCALE_PROVIDER_SOURCE_PATH; " +
                "restore it or update the source-reuse route and D-109 together",
        )
        val reusedKotlinSources =
            providerSourceDirectory
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .map { it.relativeTo(providerSourceDirectory).invariantSeparatorsPath }
                .sorted()
                .toList()
        assertEquals(
            listOf(IOS_LOCALE_PROVIDER_SOURCE_NAME),
            reusedKotlinSources,
            "D-109 permits only $IOS_LOCALE_PROVIDER_SOURCE_NAME in the reused source directory; " +
                "move additional Kotlin sources outside it or revise D-109 and its test topology",
        )
        assertTrue(
            providerTest.isFile,
            "D-109 requires iOS host behavior tests at $IOS_LOCALE_PROVIDER_TEST_PATH; " +
                "restore the tests or update D-109 and the canonical route together",
        )
        val providerTestSource = providerTest.readText()
        REQUIRED_IOS_LOCALE_PROVIDER_TESTS.forEach { testName ->
            assertTrue(
                providerTestSource.contains("fun $testName()"),
                "D-109 requires $testName in the canonical iOS host suite; " +
                    "add the missing review coverage or revise D-109",
            )
        }
        assertTrue(
            agents.contains("testAndroidHostTest iosSimulatorArm64Test"),
            "D-109 requires the root Native test task in the AGENTS.md canonical command; " +
                "restore it or update D-109 and every command mirror",
        )
        assertTrue(
            ci.contains("testAndroidHostTest iosSimulatorArm64Test"),
            "D-109 requires the root Native test task in CI; restore it or update D-109 and every " +
                "command mirror",
        )
    }

    @Test
    fun exportedCommonEnumsPinTheirExactObjectiveCAndSwiftNames() {
        val expectedNames =
            listOf(
                ExactEnumName(APP_ERROR_PATH, "SharedConfirmation", "Confirmation"),
                ExactEnumName(PLATFORM_ABSTRACTIONS_PATH, "SharedAuthProvider", "AuthProvider"),
                ExactEnumName(PLATFORM_ABSTRACTIONS_PATH, "SharedSyncTrigger", "SyncTrigger"),
            )
        val missing =
            expectedNames.filterNot { expected ->
                repositoryRoot.resolve(expected.path).readText().contains(expected.annotation)
            }

        assertEquals(emptyList(), missing)
    }

    private data class ExactEnumName(
        val path: String,
        val objectiveCName: String,
        val swiftName: String,
    ) {
        val annotation: String =
            "@ObjCName(name = \"$objectiveCName\", swiftName = \"$swiftName\", exact = true)"
    }

    private companion object {
        const val APP_ERROR_PATH =
            "core/common/src/commonMain/kotlin/com/ruizurraca/carapp/core/common/AppError.kt"
        const val PLATFORM_ABSTRACTIONS_PATH =
            "core/common/src/commonMain/kotlin/com/ruizurraca/carapp/core/common/PlatformAbstractions.kt"
        const val IOS_LOCALE_PROVIDER_TEST_PATH =
            "shared/src/iosTest/kotlin/com/ruizurraca/carapp/locale/IosLocaleProviderTest.kt"
        const val IOS_LOCALE_PROVIDER_SOURCE_DIRECTORY =
            "composition/ios/src/iosMain/kotlin/com/ruizurraca/carapp/locale"
        const val IOS_LOCALE_PROVIDER_SOURCE_NAME = "IosLocaleProvider.kt"
        const val IOS_LOCALE_PROVIDER_SOURCE_PATH =
            "$IOS_LOCALE_PROVIDER_SOURCE_DIRECTORY/$IOS_LOCALE_PROVIDER_SOURCE_NAME"
        val REQUIRED_IOS_LOCALE_PROVIDER_TESTS =
            listOf(
                "localeCurrencyOutsideTheMvpSetFallsBackToEur",
                "foundationCurrencyFractionDigitsMatchTheMvpPremise",
                "languageOnlyLocaleProvidesNullRegionAndFallsBackToEur",
            )
    }
}
