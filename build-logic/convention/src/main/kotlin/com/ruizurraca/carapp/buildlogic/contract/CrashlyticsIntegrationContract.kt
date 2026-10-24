package com.ruizurraca.carapp.buildlogic.contract

import java.io.File

/**
 * Contract for the Firebase Crashlytics integration of `E4-04`, modelled on
 * [AnalyticsIntegrationContract].
 *
 * Three rules are asserted here because no other check can see them:
 *
 * - `docs/CONTRACTS.md §11.6`, `§20.3.1` and `D-179`: the Firebase Crashlytics implementation is
 *   named only by `:wiring:firebase` and its own module, `:wiring:firebase` constructs it, it records
 *   nothing until the opt-in turns it on, and the integration never sets a provider user identifier.
 * - `D-65`, `D-75`: the iOS build links the `FirebaseCrashlytics` SwiftPM product. GitLive 2.6.0 does
 *   not supply Firebase Apple dependencies transitively, so the cinterop klib resolves to
 *   `framework 'FirebaseCrashlytics' not found` at the Xcode link step unless the app links it.
 * - `docs/CONTRACTS.md §16.1` (`D-241`): the Crashlytics SDK initialises at process start, before
 *   the graph's opt-in gate exists, so each host disables collection in its platform configuration.
 */
internal class CrashlyticsIntegrationContract internal constructor(
    private val inputs: Inputs,
) {
    constructor(repoRoot: File) : this(Inputs.from(repoRoot))

    internal data class Inputs(
        val kotlinSources: List<Pair<String, String>>,
        val reporterSource: String,
        val projectSpec: String,
        val xcodeProject: String,
        val androidManifest: String,
        val iosInfoPlist: String,
    ) {
        companion object {
            fun from(repoRoot: File): Inputs {
                val sources =
                    repoRoot
                        .walkTopDown()
                        .filter { file ->
                            file.isFile &&
                                file.extension == "kt" &&
                                "${File.separator}build${File.separator}" !in file.path
                        }.map { file ->
                            repoRoot.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/') to
                                file.readText()
                        }.toList()
                return Inputs(
                    kotlinSources = sources,
                    reporterSource =
                        repoRoot
                            .resolve(REPORTER_PATH)
                            .takeIf(File::exists)
                            ?.readText()
                            .orEmpty(),
                    projectSpec = repoRoot.resolve("iosApp/project.yml").readText(),
                    xcodeProject = repoRoot.resolve("iosApp/carApp.xcodeproj/project.pbxproj").readText(),
                    androidManifest = repoRoot.resolve(ANDROID_MANIFEST_PATH).readText(),
                    iosInfoPlist = repoRoot.resolve(IOS_INFO_PLIST_PATH).readText(),
                )
            }
        }
    }

    fun validate(): List<AssertionResult> = listOf(namingResult(), iosProductResult(), hostCollectionResult())

    private fun namingResult(): AssertionResult {
        val offenders =
            inputs.kotlinSources
                .filterNot { (path, _) ->
                    path.startsWith("wiring/firebase/") ||
                        path.startsWith(INTEGRATION_DIRECTORY) ||
                        path.startsWith("build-logic/")
                }.filter { (_, text) -> text.contains(IMPLEMENTATION_NAME) }
                .map { (path, _) -> path }

        val missing =
            if (inputs.kotlinSources.none { (path, text) ->
                    path == WIRING_PATH && text.contains("$IMPLEMENTATION_NAME()")
                }
            ) {
                listOf("$WIRING_PATH does not construct $IMPLEMENTATION_NAME")
            } else {
                emptyList()
            }

        val notDisabledByDefault =
            if (inputs.reporterSource.contains("enabled: Boolean = false")) {
                emptyList()
            } else {
                listOf("$REPORTER_PATH does not default reporting to disabled")
            }

        val publicConstructorNotDisabled =
            if (PUBLIC_CONSTRUCTOR_DISABLED.containsMatchIn(inputs.reporterSource)) {
                emptyList()
            } else {
                listOf("$REPORTER_PATH public constructor does not start with reporting disabled")
            }

        val userIdentifiers =
            inputs.kotlinSources
                .filter { (path, text) -> path.startsWith(INTEGRATION_DIRECTORY) && USER_ID_CALL.containsMatchIn(text) }
                .map { (path, _) -> "$path sets a provider user identifier" }

        return result(
            id = 46,
            name =
                "the Firebase Crashlytics implementation is named only by :wiring:firebase, " +
                    "starts disabled and sets no user identifier",
            problems = offenders + missing + notDisabledByDefault + publicConstructorNotDisabled + userIdentifiers,
        )
    }

    private fun iosProductResult(): AssertionResult {
        val problems = mutableListOf<String>()
        val specProducts =
            Regex("""(?m)^\s*product:\s*(\w+)\s*(?:#.*)?$""")
                .findAll(inputs.projectSpec)
                .map { it.groupValues[1] }
                .filter { it.startsWith(IOS_PRODUCT) }
                .toList()
        val projectProducts =
            Regex("""productName\s*=\s*(\w+)\s*;""")
                .findAll(inputs.xcodeProject)
                .map { it.groupValues[1] }
                .filter { it.startsWith(IOS_PRODUCT) }
                .toList()
        if (specProducts != listOf(IOS_PRODUCT)) {
            problems += "iosApp/project.yml must link exactly $IOS_PRODUCT; found $specProducts"
        }
        if (projectProducts != listOf(IOS_PRODUCT)) {
            problems += "the generated Xcode project must reference exactly $IOS_PRODUCT; found $projectProducts"
        }
        return result(
            id = 47,
            name = "the iOS app links the FirebaseCrashlytics SwiftPM product",
            problems = problems,
        )
    }

    private fun hostCollectionResult(): AssertionResult {
        // Parsed like assertion 39: comments are removed first and the key must be declared once, so a
        // commented-out or a second, contradicting declaration cannot satisfy the check.
        val android = inputs.androidManifest.replace(XML_COMMENTS, "")
        val ios = inputs.iosInfoPlist.replace(XML_COMMENTS, "")
        val problems = mutableListOf<String>()
        if (!androidDisabledOnce(android)) {
            problems += "$ANDROID_MANIFEST_PATH does not set $ANDROID_COLLECTION_KEY to false"
        }
        if (!iosDisabledOnce(ios)) {
            problems += "$IOS_INFO_PLIST_PATH does not set $IOS_COLLECTION_KEY to false"
        }
        return result(
            id = 48,
            name = "both hosts disable Firebase Crashlytics collection before the app graph exists",
            problems = problems,
        )
    }

    private fun iosDisabledOnce(text: String): Boolean =
        Regex("""<key>$IOS_COLLECTION_KEY</key>""").findAll(text).count() == 1 &&
            Regex("""<key>$IOS_COLLECTION_KEY</key>\s*<false\s*/>""").containsMatchIn(text)

    private fun androidDisabledOnce(text: String): Boolean {
        val declarations =
            Regex("""<meta-data\b[^>]*>""")
                .findAll(text)
                .map { it.value }
                .filter { Regex("""\bandroid:name="${Regex.escape(ANDROID_COLLECTION_KEY)}"""").containsMatchIn(it) }
                .toList()
        return declarations.size == 1 &&
            Regex("""\bandroid:value="false"""").containsMatchIn(declarations.single())
    }

    private fun result(
        id: Int,
        name: String,
        problems: List<String>,
    ): AssertionResult =
        if (problems.isEmpty()) {
            AssertionResult(id, name, AssertionResult.Status.PASS)
        } else {
            AssertionResult(id, name, AssertionResult.Status.FAIL, problems.joinToString("; "))
        }

    private companion object {
        const val IMPLEMENTATION_NAME = "FirebaseCrashReporter"
        const val IOS_PRODUCT = "FirebaseCrashlytics"
        const val INTEGRATION_DIRECTORY = "integration/firebase-crashlytics/"
        const val REPORTER_PATH =
            "integration/firebase-crashlytics/src/commonMain/kotlin/com/ruizurraca/carapp/" +
                "integration/firebase/crashlytics/FirebaseCrashReporter.kt"
        const val WIRING_PATH =
            "wiring/firebase/src/commonMain/kotlin/com/ruizurraca/carapp/wiring/firebase/FirebaseAppProviders.kt"
        const val ANDROID_MANIFEST_PATH = "androidApp/src/main/AndroidManifest.xml"
        const val IOS_INFO_PLIST_PATH = "iosApp/Info.plist"
        const val ANDROID_COLLECTION_KEY = "firebase_crashlytics_collection_enabled"
        const val IOS_COLLECTION_KEY = "FirebaseCrashlyticsCollectionEnabled"
        val XML_COMMENTS = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)
        val PUBLIC_CONSTRUCTOR_DISABLED = Regex("""constructor\(\)\s*:\s*this\(\s*enabled\s*=\s*false\s*,""")
        val USER_ID_CALL = Regex("""\.setUserId\s*\(""")
    }
}
