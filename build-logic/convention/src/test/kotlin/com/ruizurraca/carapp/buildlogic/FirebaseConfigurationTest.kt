package com.ruizurraca.carapp.buildlogic

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FirebaseConfigurationTest {
    private val repositoryRoot = File(checkNotNull(System.getProperty("carapp.repoRoot")))

    private companion object {
        const val DEVELOPMENT_PROJECT_ID = "davidruiz-carapp-dev"
        const val ANDROID_DEBUG_CONFIG = "androidApp/src/debug/google-services.json"
        const val ANDROID_STAGING_CONFIG = "androidApp/src/staging/google-services.json"
        const val IOS_DEBUG_CONFIG = "iosApp/Config/Debug/GoogleService-Info-Debug.plist"
        const val IOS_STAGING_CONFIG = "iosApp/Config/Staging/GoogleService-Info-Staging.plist"

        // `E4-03` (`D-252`): the benchmark build type measures staging, so it carries a byte copy of the
        // staging configuration; `PerformanceHarnessContractTest` pins the copy.
        const val ANDROID_BENCHMARK_CONFIG = "androidApp/src/benchmark/google-services.json"
        val ALLOWED_DEVELOPMENT_CONFIG_PATHS =
            listOf(
                ANDROID_DEBUG_CONFIG,
                ANDROID_STAGING_CONFIG,
                ANDROID_BENCHMARK_CONFIG,
                IOS_DEBUG_CONFIG,
                IOS_STAGING_CONFIG,
            )
        val RELEASE_CONFIG_PATHS =
            listOf(
                "androidApp/src/release/google-services.json",
                "iosApp/Config/Release/GoogleService-Info.plist",
            )
        val SECRET_FILE_PATTERNS =
            listOf(
                "*.keystore",
                "*.jks",
                "*.p12",
                "*.p8",
                "*.mobileprovision",
                "*.provisionprofile",
                "keystore.properties",
                "*service-account*.json",
                "*service_account*.json",
                "application_default_credentials.json",
                "local.properties",
            )
    }

    @Test
    fun googleServicesPluginIsPinnedAndAppliedOnlyToAndroidApp() {
        val catalog = repositoryRoot.resolve("gradle/libs.versions.toml").readText()
        val rootBuild = repositoryRoot.resolve("build.gradle.kts").readText()
        val androidBuild = repositoryRoot.resolve("androidApp/build.gradle.kts").readText()
        val buildScripts =
            repositoryRoot
                .walkTopDown()
                .filter { file ->
                    file.isFile &&
                        file.name == "build.gradle.kts" &&
                        "${File.separator}build${File.separator}" !in file.path
                }.toList()

        assertTrue(catalog.contains("googleServices = \"4.5.0\""))
        assertTrue(
            catalog.contains(
                "google-services = { id = \"com.google.gms.google-services\", " +
                    "version.ref = \"googleServices\" }",
            ),
        )
        assertTrue(rootBuild.contains("alias(libs.plugins.google.services) apply false"))
        assertTrue(androidBuild.contains("id(\"com.google.gms.google-services\")"))
        assertEquals(
            listOf(repositoryRoot.resolve("androidApp/build.gradle.kts").canonicalFile),
            buildScripts
                .filter { it.readText().contains("com.google.gms.google-services") }
                .map(File::getCanonicalFile)
                .sortedBy(File::getPath),
        )
    }

    @Test
    fun crashlyticsPluginIsPinnedAndAppliedOnlyToAndroidApp() {
        val catalog = repositoryRoot.resolve("gradle/libs.versions.toml").readText()
        val rootBuild = repositoryRoot.resolve("build.gradle.kts").readText()
        val androidBuild = repositoryRoot.resolve("androidApp/build.gradle.kts").readText()
        val buildScripts =
            repositoryRoot
                .walkTopDown()
                .filter { file ->
                    file.isFile &&
                        file.name == "build.gradle.kts" &&
                        "${File.separator}build${File.separator}" !in file.path
                }.toList()
        val googleServices = androidBuild.indexOf("id(\"com.google.gms.google-services\")")
        val crashlytics = androidBuild.indexOf("id(\"com.google.firebase.crashlytics\")")

        assertTrue(catalog.contains("crashlyticsGradle = \"3.0.8\""))
        assertTrue(
            catalog.contains(
                "firebase-crashlytics = { id = \"com.google.firebase.crashlytics\", " +
                    "version.ref = \"crashlyticsGradle\" }",
            ),
        )
        assertTrue(rootBuild.contains("alias(libs.plugins.firebase.crashlytics) apply false"))
        assertTrue(crashlytics > googleServices && googleServices >= 0, "applied after Google Services")
        assertEquals(
            listOf(repositoryRoot.resolve("androidApp/build.gradle.kts").canonicalFile),
            buildScripts
                .filter { it.readText().contains("id(\"com.google.firebase.crashlytics\")") }
                .map(File::getCanonicalFile)
                .sortedBy(File::getPath),
        )
    }

    @Test
    fun firebaseAppleSdkPinIsConsumedByTheDirectIosIntegration() {
        val catalog = repositoryRoot.resolve("gradle/libs.versions.toml").readText()
        val projectSpec = repositoryRoot.resolve("iosApp/project.yml").readText()
        val projectGenerator = repositoryRoot.resolve("iosApp/generate-project.sh").readText()
        val xcodeProject = repositoryRoot.resolve("iosApp/carApp.xcodeproj/project.pbxproj").readText()
        val resolvedPackages =
            repositoryRoot
                .resolve("iosApp/carApp.xcodeproj/project.xcworkspace/xcshareddata/swiftpm/Package.resolved")
                .readText()

        assertTrue(catalog.contains("firebaseApple = \"11.8.0\""))
        assertTrue(projectSpec.contains("exactVersion: ${'$'}{FIREBASE_APPLE_VERSION}"))
        assertFalse(projectSpec.contains("11.8.0"))
        assertTrue(projectGenerator.contains("^firebaseApple = "))
        assertTrue(projectGenerator.contains("FIREBASE_APPLE_VERSION"))
        assertTrue(projectGenerator.contains("xcodegen generate"))
        assertTrue(xcodeProject.contains("firebase-ios-sdk"))
        assertTrue(xcodeProject.contains("version = 11.8.0"))
        assertTrue(xcodeProject.contains("FirebaseAuth"))
        assertTrue(xcodeProject.contains("FirebaseCore"))
        assertTrue(xcodeProject.contains("FirebaseFirestore"))
        assertTrue(resolvedPackages.contains("\"identity\" : \"firebase-ios-sdk\""))
        assertTrue(resolvedPackages.contains("\"version\" : \"11.8.0\""))
    }

    @Test
    fun appCheckProvidersAreRestrictedToTheirIntendedNativeBuilds() {
        val catalog = repositoryRoot.resolve("gradle/libs.versions.toml").readText()
        val androidBuild = repositoryRoot.resolve("androidApp/build.gradle.kts").readText()
        val androidManifest = repositoryRoot.resolve("androidApp/src/main/AndroidManifest.xml").readText()
        val androidApplication =
            repositoryRoot
                .resolve("androidApp/src/main/java/com/ruizurraca/carapp/CarAppApplication.kt")
                .readText()
        val androidDebugProvider =
            repositoryRoot
                .resolve("androidApp/src/debug/java/com/ruizurraca/carapp/AppCheckProvider.kt")
                .readText()
        val androidReleaseProvider =
            repositoryRoot
                .resolve("androidApp/src/release/java/com/ruizurraca/carapp/AppCheckProvider.kt")
                .readText()
        val iosProject = repositoryRoot.resolve("iosApp/project.yml").readText()
        val iosApplication = repositoryRoot.resolve("iosApp/carAppApp.swift").readText()
        val iosProvider = repositoryRoot.resolve("iosApp/AppCheckProviderFactory.swift").readText()
        val iosEntitlements = repositoryRoot.resolve("iosApp/carApp.entitlements").readText()

        assertTrue(catalog.contains("firebase-appcheck-playintegrity"))
        assertTrue(catalog.contains("firebase-appcheck-debug"))
        assertTrue(androidBuild.contains("implementation(platform(libs.firebase.bom))"))
        assertTrue(androidBuild.contains("implementation(libs.firebase.appcheck.playintegrity)"))
        assertTrue(androidBuild.contains("debugImplementation(libs.firebase.appcheck.debug)"))
        assertFalse(androidBuild.contains("implementation(libs.firebase.appcheck.debug)"))
        assertTrue(androidManifest.contains("android:name=\".CarAppApplication\""))
        assertTrue(androidApplication.contains("FirebaseApp.initializeApp(this)"))
        assertTrue(androidApplication.contains("installAppCheckProviderFactory(appCheckProviderFactory())"))
        assertFalse(androidApplication.contains("DebugAppCheckProviderFactory"))
        assertTrue(androidDebugProvider.contains("DebugAppCheckProviderFactory.getInstance()"))
        assertTrue(androidDebugProvider.contains("PlayIntegrityAppCheckProviderFactory.getInstance()"))
        assertTrue(androidDebugProvider.contains("isProbablyEmulator()"))
        assertFalse(androidReleaseProvider.contains("DebugAppCheckProviderFactory"))
        assertTrue(androidReleaseProvider.contains("PlayIntegrityAppCheckProviderFactory.getInstance()"))

        assertTrue(iosProject.contains("product: FirebaseAppCheck"))
        assertTrue(iosProject.contains("CODE_SIGN_ENTITLEMENTS: carApp.entitlements"))
        assertTrue(iosApplication.contains("import FirebaseAppCheck"))
        // The App Check provider must be installed before Firebase Core is configured, whatever call
        // shape the configuration takes (`configure(options:)` since `D-259`).
        assertTrue(
            iosApplication.indexOf("configureAppCheck()") <
                iosApplication.indexOf("configureFirebase()", startIndex = iosApplication.indexOf("configureAppCheck()")),
        )
        // `D-259`: the plist follows the configuration name, not `#if DEBUG`, and the debug provider stays
        // `#if DEBUG` so Staging can never use a debug token.
        assertTrue(iosApplication.contains("firebaseConfigurationResourceName(configuration: currentConfiguration)"))
        assertTrue(iosApplication.contains("\"GoogleService-Info-Debug\""))
        assertTrue(iosApplication.contains("FirebaseOptions(contentsOfFile:"))
        val plistChoiceBody =
            iosApplication.substringAfter("private func configureFirebase()").substringBefore("/// The configuration name")
        assertFalse(
            plistChoiceBody.lines().any { line -> line.trimStart().startsWith("#if") },
            "the plist choice must not depend on a compilation condition",
        )
        assertTrue(iosProvider.contains("#if DEBUG && targetEnvironment(simulator)"))
        assertTrue(iosProvider.contains("AppCheckDebugProviderFactory"))
        assertTrue(iosProvider.contains("AppAttestProvider"))
        assertTrue(iosEntitlements.contains("com.apple.developer.devicecheck.appattest-environment"))
        assertTrue(iosEntitlements.contains("<string>production</string>"))
    }

    @Test
    fun eachIosConfigurationBundlesOnlyItsOwnFirebasePlist() {
        // `D-238` as amended by `D-261`: every iOS Firebase plist has a file name of its own, and each
        // configuration excludes every plist that is not its own. `EXCLUDED_SOURCE_FILE_NAMES` matches
        // file names, so two plists sharing a name could not be told apart, and a Release bundle would
        // carry the development configuration under the name Release loads.
        val projectSpec = repositoryRoot.resolve("iosApp/project.yml").readText()
        val xcodeProject = repositoryRoot.resolve("iosApp/carApp.xcodeproj/project.pbxproj").readText()
        val iosApplication = repositoryRoot.resolve("iosApp/carAppApp.swift").readText()
        val ownPlist =
            mapOf(
                "Debug" to "GoogleService-Info-Debug.plist",
                "Staging" to "GoogleService-Info-Staging.plist",
                "Release" to "GoogleService-Info.plist",
                // `E4-03` (`D-253`): Benchmark measures Staging, so it bundles the Staging plist.
                "Benchmark" to "GoogleService-Info-Staging.plist",
            )
        val resourcePlists =
            Regex("""(?m)^\s*- path: Config/[^/\s]+/(GoogleService-Info[^/\s]*\.plist)\s*$""")
                .findAll(projectSpec)
                .map { match -> match.groupValues[1] }
                .toList()

        assertEquals(resourcePlists.distinct(), resourcePlists, "two Firebase plist resources share a file name")
        assertEquals(
            listOf("GoogleService-Info-Debug.plist", "GoogleService-Info-Staging.plist"),
            resourcePlists.filterNot { name -> name == "GoogleService-Info.plist" }.sorted(),
            "Debug and Staging each bundle their own development plist",
        )
        val appConfigs =
            projectSpec
                .substringAfter("      configs:\n", missingDelimiterValue = "")
                .substringBefore("  carAppTests:", missingDelimiterValue = "")
        ownPlist.forEach { (configuration, plist) ->
            val excluded =
                appConfigs
                    .substringAfter("        $configuration:\n", missingDelimiterValue = "")
                    .lineSequence()
                    .takeWhile { line -> line.startsWith("          ") }
                    .firstOrNull { line -> line.trim().startsWith("EXCLUDED_SOURCE_FILE_NAMES:") }
                    ?.substringAfter(":")
                    ?.trim()
                    ?.removeSurrounding("\"")
                    ?.split(Regex("""\s+"""))
                    ?.toSet()
                    .orEmpty()
            val expected = ownPlist.values.toSet() - plist
            assertEquals(expected, excluded, "$configuration must exclude every Firebase plist except $plist")
            assertTrue(
                xcodeProject.contains("EXCLUDED_SOURCE_FILE_NAMES = \"${expected.sorted().joinToString(" ")}\";"),
                "the generated Xcode project must carry the $configuration exclusions; regenerate it with XcodeGen",
            )
        }
        assertTrue(iosApplication.contains("\"GoogleService-Info-Staging\""), "Staging must load its own plist")
        assertTrue(projectSpec.contains("plist=\"\$SRCROOT/Config/Staging/GoogleService-Info-Staging.plist\""))
    }

    @Test
    fun theStagingVariantRunsReleaseCodeAgainstTheDevelopmentProject() {
        // `D-259`: staging is the only build that exercises release code against a backend today, so it
        // must inherit release settings, keep the debug identity and never see the debug App Check
        // provider, which lives in the `debug` source set.
        val androidBuild = repositoryRoot.resolve("androidApp/build.gradle.kts").readText()
        val stagingBlock =
            androidBuild
                .substringAfter("create(\"staging\")", missingDelimiterValue = "")
                .substringBefore("matchingFallbacks", missingDelimiterValue = "")
        val projectSpec = repositoryRoot.resolve("iosApp/project.yml").readText()
        val stagingIos =
            projectSpec.substringAfter("    Staging:", missingDelimiterValue = "")
                .substringBefore("  carAppTests:", missingDelimiterValue = "")

        assertTrue(stagingBlock.isNotEmpty(), "the staging build type must exist")
        assertTrue(androidBuild.contains("initWith(release)") || stagingBlock.contains("release"))
        assertTrue(stagingBlock.contains("applicationIdSuffix = \".debug\""))
        assertTrue(stagingBlock.contains("signingConfig"))
        assertTrue(projectSpec.contains("  Staging: release"), "Staging must declare release settings")
        assertTrue(stagingIos.contains("PRODUCT_BUNDLE_IDENTIFIER: com.ruizurraca.carapp.debug"))
        assertTrue(stagingIos.contains("KOTLIN_FRAMEWORK_BUILD_TYPE: release"))
    }

    @Test
    fun androidReleaseSigningIsReadFromAnOutOfRepositoryPropertiesFile() {
        // `D-257`: the release is signed only where an out-of-repository properties file exists; a fresh
        // clone and CI stay unsigned and silent, and a path inside the repository is refused.
        val androidBuild = repositoryRoot.resolve("androidApp/build.gradle.kts").readText()
        val ignoredPaths = repositoryRoot.resolve(".gitignore").readLines().map(String::trim)

        assertTrue(androidBuild.contains("carapp.releaseSigningProperties"))
        assertTrue(
            androidBuild.contains("check(!signingFile.startsWith(repositoryRoot))"),
            "a signing properties file inside the repository must be refused",
        )
        assertTrue(
            androidBuild.contains("rootProject.projectDir") || androidBuild.contains("rootDir"),
            "the repository root must be known so an in-repository path can be refused",
        )
        assertTrue(androidBuild.contains("signingConfig"), "the release build type must consume the config")
        assertTrue(ignoredPaths.contains("keystore.properties"))
        assertFalse(
            androidBuild.contains("storePassword = \""),
            "no password may be a literal in the build script",
        )
    }

    @Test
    fun releaseFirebaseConfigurationIsAbsentOrStrictlyProduction() {
        // `D-238`: `E4-04` ships the mechanism and the fail-closed state; `E4-10` adds the files. An
        // absent release configuration is therefore correct today, and a present one must never name
        // the development project.
        val releaseConfigs = RELEASE_CONFIG_PATHS.map { repositoryRoot.resolve(it) }

        releaseConfigs.filter(File::exists).forEach { file ->
            val text = file.readText()
            assertFalse(
                text.contains(DEVELOPMENT_PROJECT_ID),
                "${file.name} must not name $DEVELOPMENT_PROJECT_ID",
            )
        }
        assertEquals(
            emptyList(),
            releaseConfigs.map(File::getPath).filter { path -> repositoryRoot.resolve(path).isDirectory },
            "a release configuration path must be a file, not a directory",
        )
    }

    @Test
    fun developmentConfigurationIsAllowedOnlyInTheReviewedLocations() {
        // `D-259`: development configuration is allowed in Debug and in Staging, never in Release.
        val files =
            repositoryRoot.walkTopDown()
                .filter { file ->
                    file.isFile &&
                        (file.name == "google-services.json" || file.name.startsWith("GoogleService-Info")) &&
                        "${File.separator}build${File.separator}" !in file.path
                }.map { file ->
                    repositoryRoot.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/')
                }.sorted()
                .toList()

        assertEquals(ALLOWED_DEVELOPMENT_CONFIG_PATHS.sorted(), files)
    }

    @Test
    fun gitignoreCoversEverySecretFileKindTheSecurityPolicyNames() {
        val ignored = repositoryRoot.resolve(".gitignore").readLines().map(String::trim)

        SECRET_FILE_PATTERNS.forEach { pattern ->
            assertTrue(ignored.contains(pattern), ".gitignore must ignore $pattern (docs/SECURITY.md)")
        }
    }

    @Test
    fun iosSimulatorUsesNormalXcodeSigningWithoutACommittedIdentity() {
        val projectSpec = repositoryRoot.resolve("iosApp/project.yml").readText()
        val xcodeProject = repositoryRoot.resolve("iosApp/carApp.xcodeproj/project.pbxproj").readText()

        assertFalse(projectSpec.contains("CODE_SIGNING_ALLOWED: NO"))
        assertFalse(projectSpec.contains("CODE_SIGNING_REQUIRED: NO"))
        assertFalse(projectSpec.contains("CODE_SIGN_IDENTITY:"))
        assertFalse(projectSpec.contains("DEVELOPMENT_TEAM:"))
        assertFalse(xcodeProject.contains("CODE_SIGNING_ALLOWED = NO;"))
        assertFalse(xcodeProject.contains("CODE_SIGNING_REQUIRED = NO;"))
        assertTrue(xcodeProject.contains("CODE_SIGN_IDENTITY = \"iPhone Developer\";"))
        assertFalse(xcodeProject.contains("DEVELOPMENT_TEAM ="))
    }

    @Test
    fun iosDeveloperTeamIsSuppliedByAnUntrackedLocalConfiguration() {
        val projectSpec = repositoryRoot.resolve("iosApp/project.yml").readText()
        val xcodeProject = repositoryRoot.resolve("iosApp/carApp.xcodeproj/project.pbxproj").readText()
        val signingConfiguration = repositoryRoot.resolve("iosApp/Signing.xcconfig").readText()
        val ignoredPaths = repositoryRoot.resolve(".gitignore").readLines().map(String::trim)

        // `D-238` inserted a per-configuration base into the chain, because the Google URL scheme differs
        // per configuration. Signing keeps its own file: each Firebase.xcconfig includes
        // Signing.xcconfig, and that includes the untracked Local.xcconfig.
        assertTrue(projectSpec.contains("Config/Debug/Firebase.xcconfig"))
        assertTrue(projectSpec.contains("Config/Release/Firebase.xcconfig"))
        assertTrue(projectSpec.contains("Config/Staging/Firebase.xcconfig"))
        assertEquals(
            4,
            xcodeProject.lines().count { it.contains("baseConfigurationReference") && it.contains("Firebase.xcconfig") },
            "each of the four app configurations must carry a Firebase base configuration (Benchmark reuses Staging's)",
        )
        (listOf("Debug", "Release", "Staging")).forEach { configuration ->
            val base = repositoryRoot.resolve("iosApp/Config/$configuration/Firebase.xcconfig").readText()
            assertTrue(
                base.contains("#include \"../../Signing.xcconfig\""),
                "$configuration must reach the signing configuration",
            )
        }
        assertTrue(signingConfiguration.contains("#include? \"Local.xcconfig\""))
        assertTrue(ignoredPaths.contains("iosApp/Local.xcconfig"))
        assertFalse(signingConfiguration.contains("QGPVH5G7ST"))
    }

    @Test
    fun iosKeychainPersistenceAcceptanceUsesASeparateTestOnlyUiTarget() {
        val projectSpec = repositoryRoot.resolve("iosApp/project.yml").readText()
        val appEntitlements = repositoryRoot.resolve("iosApp/carApp.entitlements").readText()
        val sharedScheme =
            repositoryRoot
                .resolve("iosApp/carApp.xcodeproj/xcshareddata/xcschemes/carApp.xcscheme")
                .readText()
        val uiTest =
            repositoryRoot
                .resolve("iosApp/UITests/CarAppKeychainPersistenceUITests.swift")
                .readText()
        val uiTestBuildEntry =
            sharedScheme
                .substringBefore("BuildableName = \"carAppUITests.xctest\"")
                .substringAfterLast("<BuildActionEntry")

        assertTrue(projectSpec.contains("carAppUITests:"))
        assertTrue(projectSpec.contains("type: bundle.ui-testing"))
        assertTrue(projectSpec.contains("PRODUCT_BUNDLE_IDENTIFIER: com.ruizurraca.carapp.uitests"))
        assertTrue(projectSpec.contains("SKIP_INSTALL: YES"))
        assertTrue(projectSpec.contains("SUPPORTED_PLATFORMS: iphonesimulator"))
        assertTrue(projectSpec.contains("EXCLUDED_SOURCE_FILE_NAMES: CarAppKeychainPersistenceUITests.swift"))
        assertTrue(uiTestBuildEntry.contains("buildForTesting = \"YES\""))
        assertTrue(uiTestBuildEntry.contains("buildForRunning = \"NO\""))
        assertTrue(uiTestBuildEntry.contains("buildForProfiling = \"NO\""))
        assertTrue(uiTestBuildEntry.contains("buildForArchiving = \"NO\""))
        assertTrue(uiTestBuildEntry.contains("buildForAnalyzing = \"NO\""))
        assertFalse(appEntitlements.contains("keychain-access-groups"))
        assertFalse(projectSpec.contains("KEYCHAIN_ACCESS_GROUPS"))
        assertTrue(uiTest.contains("XCUIApplication()"))
        assertTrue(uiTest.contains("app.terminate()"))
        assertTrue(uiTest.substringAfter("app.terminate()").contains("app.launch()"))
        assertTrue(uiTest.contains("app.staticTexts"))
        assertFalse(uiTest.contains("@testable import"))
    }
}
