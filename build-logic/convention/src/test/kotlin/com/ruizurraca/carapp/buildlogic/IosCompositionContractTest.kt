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
            ci.contains(":androidApp:testDebugUnitTest testAndroidHostTest"),
            "D-109 requires the Android-host aggregate in CI; restore it or update D-109 and every " +
                "command mirror",
        )
        assertTrue(
            ci.contains("./gradlew iosSimulatorArm64Test"),
            "D-109 requires the root Native test task in CI; restore it or update D-109 and every " +
                "command mirror",
        )
    }

    @Test
    fun theSingleBackgroundTaskIdentifierIsDeclaredInBothPlacesThatMustAgree() {
        val scheduling = repositoryRoot.resolve(IOS_SCHEDULING_PATH).readText()
        val infoPlist = repositoryRoot.resolve(IOS_INFO_PLIST_PATH).readText()

        // `§9.1` permits exactly one `BGTaskScheduler` identifier. It is written twice - once in the
        // Kotlin registration and once in the Info.plist allowlist the platform reads - and the two
        // are not connected by the compiler. A divergence fails only at runtime, with an NSLog line
        // and a silently dead six-hour cadence, so the agreement is asserted here instead.
        val declared =
            Regex("""const val SYNC_TASK_IDENTIFIER = "([^"]+)"""")
                .find(scheduling)
                ?.groupValues
                ?.get(1)
        assertTrue(declared != null, "IosSyncScheduling MUST declare SYNC_TASK_IDENTIFIER as a string constant")
        assertTrue(
            infoPlist.contains("<key>BGTaskSchedulerPermittedIdentifiers</key>"),
            "Info.plist MUST permit the background task identifier, or registration is refused",
        )
        assertTrue(
            infoPlist.contains("<string>$declared</string>"),
            "Info.plist MUST permit exactly the identifier the Kotlin registration uses; declared=$declared",
        )
        assertTrue(
            infoPlist.contains("<key>UIBackgroundModes</key>"),
            "a BGAppRefreshTask requires the fetch background mode",
        )
        // One identifier, not two (`§9.1`).
        assertEquals(
            1,
            Regex("""const val SYNC_TASK_IDENTIFIER""").findAll(scheduling).count(),
            "§9.1 permits a single BGTaskScheduler identifier",
        )
    }

    @Test
    fun theSingleBackgroundTaskIdentifierIsRegisteredInTheIdentifierRegistry() {
        val identifiers = repositoryRoot.resolve(IOS_IDENTIFIERS_PATH).readText()

        // `docs/identifiers.md` is the canonical registry `AGENTS.md` forbids agents to bypass, and it
        // did not mention the scheduler identifier at all. It is not a bundle identifier, so the
        // registry row is the only place it is recorded as shared by Debug and Release.
        assertTrue(
            identifiers.contains("com.ruizurraca.carapp.sync"),
            "docs/identifiers.md MUST register the single BGTaskScheduler identifier",
        )
    }

    @Test
    fun bothPlatformLeasesAwaitThePeriodicCycleBeforeReportingCompletion() {
        val ios = repositoryRoot.resolve(IOS_SCHEDULING_PATH).readText()

        // `D-187`: `setTaskCompletedWithSuccess` tells iOS the task has ended, so completing before
        // the cycle finished would let the system suspend the process mid-cycle.
        assertTrue(
            ios.contains("sync(SyncTrigger.Periodic)"),
            "the iOS handler MUST await the periodic cycle on the process graph",
        )
        assertTrue(
            ios.contains("expirationHandler"),
            "the iOS handler MUST install an expiration handler for an overrunning cycle",
        )
        // The handler is installed before the job starts, so an expiry arriving immediately cannot
        // leave the task with nothing to complete it.
        assertTrue(
            ios.indexOf("expirationHandler") < ios.indexOf("syncJob.start()"),
            "the expiration handler MUST be installed before the cycle is started",
        )
        // Exactly one completion call site, guarded so the expiry and the cycle cannot both complete
        // it. The receiver is part of the pattern so a mention inside a comment is not counted.
        val completions = Regex("\\.setTaskCompletedWithSuccess\\(").findAll(ios).count()
        assertTrue(
            completions == 1,
            "iOS MUST call setTaskCompletedWithSuccess exactly once, from the idempotent gate; found $completions",
        )
        assertTrue(
            ios.contains("class BackgroundTaskCompletion"),
            "the single completion MUST be guarded by the idempotent gate",
        )
        // The fire-and-forget periodic request is gone from the platform path.
        assertFalse(
            ios.contains("requestSync(SyncTrigger.Periodic)"),
            "the iOS platform lease MUST NOT fire and forget the periodic cycle",
        )
    }

    @Test
    fun bothHostsInjectRealPlatformConnectivityIntoTheProviderGraph() {
        val swiftFactory = repositoryRoot.resolve(CREATE_SWIFT_APP_GRAPH_PATH).readText()
        // The Android graph is process-scoped (`§9.1`), so the real connectivity observer is
        // constructed by the file that builds the graph rather than by the Activity.
        val androidHost = repositoryRoot.resolve(ANDROID_APP_GRAPH_PATH).readText()
        val androidManifest = repositoryRoot.resolve(ANDROID_MANIFEST_PATH).readText()
        val firebaseProviders = repositoryRoot.resolve(FIREBASE_PROVIDERS_PATH).readText()
        val iosObserverDirectory = repositoryRoot.resolve(IOS_CONNECTIVITY_SOURCE_DIRECTORY)
        val sharedBuild = repositoryRoot.resolve("shared/build.gradle.kts").readText()

        assertTrue(
            swiftFactory.contains("connectivityObserver = IosConnectivityObserver"),
            "the iOS composition root MUST inject real connectivity, not the staged default",
        )
        assertTrue(
            androidHost.contains("connectivityObserver = AndroidConnectivityObserver"),
            "the Android host MUST inject real connectivity, not the staged default",
        )
        assertTrue(
            androidManifest.contains("android.permission.ACCESS_NETWORK_STATE"),
            "real Android connectivity observation requires ACCESS_NETWORK_STATE",
        )
        assertFalse(
            firebaseProviders.contains("MutableStateFlow(true)"),
            "the always-online connectivity stub MUST NOT survive in the production provider graph",
        )
        assertTrue(
            iosObserverDirectory.resolve(IOS_CONNECTIVITY_SOURCE_NAME).isFile,
            "the composition-owned iOS observer MUST live at $IOS_CONNECTIVITY_SOURCE_DIRECTORY",
        )
        assertEquals(
            listOf(IOS_CONNECTIVITY_SOURCE_NAME),
            iosObserverDirectory
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .map { it.relativeTo(iosObserverDirectory).invariantSeparatorsPath }
                .sorted()
                .toList(),
            "only the observer belongs in the reused source directory",
        )
        assertTrue(
            sharedBuild.contains(IOS_CONNECTIVITY_SOURCE_DIRECTORY),
            "shared iosTest MUST reuse the composition-owned observer source, as D-109 does for locale",
        )
    }

    @Test
    fun exportedCommonEnumsPinTheirExactObjectiveCAndSwiftNames() {
        val expectedNames =
            listOf(
                ExactEnumName(APP_ERROR_PATH, "SharedConfirmation", "Confirmation"),
                ExactEnumName(PLATFORM_ABSTRACTIONS_PATH, "SharedAuthProvider", "AuthProvider"),
                ExactEnumName(PLATFORM_ABSTRACTIONS_PATH, "SharedSyncTrigger", "SyncTrigger"),
                ExactEnumName(UI_MODELS_PATH, "SharedNativeSignInFailure", "NativeSignInFailure"),
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
        const val CREATE_SWIFT_APP_GRAPH_PATH =
            "composition/ios/src/iosMain/kotlin/com/ruizurraca/carapp/CreateSwiftAppGraph.kt"
        const val ANDROID_APP_GRAPH_PATH =
            "androidApp/src/main/java/com/ruizurraca/carapp/AndroidAppGraph.kt"
        const val ANDROID_MANIFEST_PATH = "androidApp/src/main/AndroidManifest.xml"
        const val IOS_SCHEDULING_PATH =
            "composition/ios/src/iosMain/kotlin/com/ruizurraca/carapp/scheduling/IosSyncScheduling.kt"
        const val IOS_INFO_PLIST_PATH = "iosApp/Info.plist"
        const val IOS_IDENTIFIERS_PATH = "docs/identifiers.md"
        const val FIREBASE_PROVIDERS_PATH =
            "wiring/firebase/src/commonMain/kotlin/com/ruizurraca/carapp/wiring/firebase/FirebaseAppProviders.kt"
        const val IOS_CONNECTIVITY_SOURCE_DIRECTORY =
            "composition/ios/src/iosMain/kotlin/com/ruizurraca/carapp/connectivity"
        const val IOS_CONNECTIVITY_SOURCE_NAME = "IosConnectivityObserver.kt"
        const val APP_ERROR_PATH =
            "core/common/src/commonMain/kotlin/com/ruizurraca/carapp/core/common/AppError.kt"
        const val PLATFORM_ABSTRACTIONS_PATH =
            "core/common/src/commonMain/kotlin/com/ruizurraca/carapp/core/common/PlatformAbstractions.kt"
        const val UI_MODELS_PATH =
            "shared/src/commonMain/kotlin/com/ruizurraca/carapp/UiModels.kt"
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
