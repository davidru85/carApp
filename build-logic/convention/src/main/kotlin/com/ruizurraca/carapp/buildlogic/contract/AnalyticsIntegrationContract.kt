package com.ruizurraca.carapp.buildlogic.contract

import java.io.File

/**
 * Contract for the Firebase Analytics integration of `E3-09`.
 *
 * Four rules are asserted here because no other check can see them:
 *
 * - `docs/CONTRACTS.md §11.6` and `D-179`: the Firebase Analytics implementation is named only by
 *   `:wiring:firebase`. The architecture checker enforces that for the `:integration:*` package
 *   prefix; this asserts it for the public class name as well, because a platform host could reach
 *   the implementation through a re-export or a fully-qualified name that carries no package prefix
 *   on the offending line.
 * - `D-65`, `D-203`: the iOS build links `FirebaseAnalyticsWithoutAdIdSupport`. GitLive
 *   2.6.0 does not supply Firebase Apple dependencies transitively, so the cinterop klib the
 *   Kotlin/Native compiler consumes resolves to `framework 'FirebaseAnalytics' not found` unless the
 *   host app links the product. Nothing else in the repository can observe that coupling: the Kotlin
 *   build succeeds either way and the failure appears only in the Xcode link step.
 * - `docs/CONTRACTS.md §16.1`: the provider SDK initialises at process start, before any
 *   `AnalyticsTracker` exists, so each host disables collection in its platform configuration.
 *   Without it a fresh install collects the provider's automatic events before the tracker can
 *   command the provider off.
 * - `D-203`: both hosts disable advertising identification, personalized ad signals and automatic
 *   screen reporting; Android removes advertising permissions and iOS disables IDFV collection.
 */
internal class AnalyticsIntegrationContract internal constructor(
    private val inputs: Inputs,
) {
    constructor(repoRoot: File) : this(Inputs.from(repoRoot))

    internal data class Inputs(
        val kotlinSources: List<Pair<String, String>>,
        val trackerSource: String,
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
                    trackerSource =
                        repoRoot
                            .resolve(TRACKER_PATH)
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

    fun validate(): List<AssertionResult> =
        listOf(namingResult(), iosProductResult(), hostCollectionResult(), hostPrivacyResult())

    private fun namingResult(): AssertionResult {
        val offenders =
            inputs.kotlinSources
                .filterNot { (path, _) ->
                    path.startsWith("wiring/firebase/") ||
                        path.startsWith("integration/firebase-analytics/") ||
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
            if (inputs.trackerSource.contains("enabled: Boolean = false")) {
                emptyList()
            } else {
                listOf("$TRACKER_PATH does not default collection to disabled")
            }

        val publicConstructorNotDisabled =
            if (PUBLIC_CONSTRUCTOR_DISABLED.containsMatchIn(inputs.trackerSource)) {
                emptyList()
            } else {
                listOf("$TRACKER_PATH public constructor does not start with collection disabled")
            }

        return result(
            id = 37,
            name = "the Firebase Analytics implementation is named only by :wiring:firebase and starts disabled",
            problems = offenders + missing + notDisabledByDefault + publicConstructorNotDisabled,
        )
    }

    private fun iosProductResult(): AssertionResult {
        val problems = mutableListOf<String>()
        val specProducts =
            Regex("""(?m)^\s*product:\s*(\w+)\s*(?:#.*)?$""")
                .findAll(inputs.projectSpec)
                .map { it.groupValues[1] }
                .filter { it.startsWith("FirebaseAnalytics") }
                .toList()
        val projectProducts =
            Regex("""productName\s*=\s*(\w+)\s*;""")
                .findAll(inputs.xcodeProject)
                .map { it.groupValues[1] }
                .filter { it.startsWith("FirebaseAnalytics") }
                .toList()
        if (specProducts != listOf(IOS_PRODUCT)) {
            problems += "iosApp/project.yml must link exactly $IOS_PRODUCT; found $specProducts"
        }
        if (projectProducts != listOf(IOS_PRODUCT)) {
            problems += "the generated Xcode project must reference exactly $IOS_PRODUCT; found $projectProducts"
        }
        return result(
            id = 38,
            name = "the iOS app links the FirebaseAnalyticsWithoutAdIdSupport SwiftPM product",
            problems = problems,
        )
    }

    private fun hostCollectionResult(): AssertionResult {
        // Parsed exactly like the privacy settings of assertion 40: comments are removed first and
        // the key must be declared once, so a commented-out or a second, contradicting declaration
        // cannot satisfy the check.
        val android = inputs.androidManifest.replace(XML_COMMENTS, "")
        val ios = inputs.iosInfoPlist.replace(XML_COMMENTS, "")
        val problems = mutableListOf<String>()
        if (!androidSetting(android, "meta-data", ANDROID_COLLECTION_KEY, "android:value", "false")) {
            problems += "$ANDROID_MANIFEST_PATH does not set $ANDROID_COLLECTION_KEY to false"
        }
        if (!iosDisabledOnce(ios, IOS_COLLECTION_KEY)) {
            problems += "$IOS_INFO_PLIST_PATH does not set $IOS_COLLECTION_KEY to false"
        }
        return result(
            id = 39,
            name = "both hosts disable Firebase Analytics collection before the app graph exists",
            problems = problems,
        )
    }

    private fun hostPrivacyResult(): AssertionResult {
        val android = inputs.androidManifest.replace(XML_COMMENTS, "")
        val ios = inputs.iosInfoPlist.replace(XML_COMMENTS, "")
        val problems = mutableListOf<String>()
        ANDROID_PRIVACY_KEYS.forEach { key ->
            if (!androidSetting(android, "meta-data", key, "android:value", "false")) {
                problems += "$ANDROID_MANIFEST_PATH does not disable $key"
            }
        }
        IOS_PRIVACY_KEYS.forEach { key ->
            if (!iosDisabledOnce(ios, key)) {
                problems += "$IOS_INFO_PLIST_PATH does not disable $key"
            }
        }
        ADVERTISING_PERMISSIONS.forEach { permission ->
            if (!androidSetting(android, "uses-permission", permission, "tools:node", "remove")) {
                problems += "$ANDROID_MANIFEST_PATH does not remove $permission"
            }
        }
        return result(
            id = 40,
            name = "both hosts enforce the analytics policy without advertising identification",
            problems = problems,
        )
    }

    private fun iosDisabledOnce(
        text: String,
        key: String,
    ): Boolean =
        Regex("""<key>$key</key>""").findAll(text).count() == 1 &&
            Regex("""<key>$key</key>\s*<false\s*/>""").containsMatchIn(text)

    private fun androidSetting(
        text: String,
        element: String,
        key: String,
        attribute: String,
        value: String,
    ): Boolean {
        val declarations =
            Regex("""<$element\b[^>]*>""")
                .findAll(text)
                .map { it.value }
                .filter { Regex("""\bandroid:name="${Regex.escape(key)}"""").containsMatchIn(it) }
                .toList()
        return declarations.size == 1 &&
            Regex("""\b$attribute="$value"""").containsMatchIn(declarations.single())
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
        const val IMPLEMENTATION_NAME = "FirebaseAnalyticsTracker"
        const val IOS_PRODUCT = "FirebaseAnalyticsWithoutAdIdSupport"
        const val TRACKER_PATH =
            "integration/firebase-analytics/src/commonMain/kotlin/com/ruizurraca/carapp/" +
                "integration/firebase/analytics/FirebaseAnalyticsTracker.kt"
        const val WIRING_PATH =
            "wiring/firebase/src/commonMain/kotlin/com/ruizurraca/carapp/wiring/firebase/FirebaseAppProviders.kt"
        const val ANDROID_MANIFEST_PATH = "androidApp/src/main/AndroidManifest.xml"
        const val IOS_INFO_PLIST_PATH = "iosApp/Info.plist"
        const val ANDROID_COLLECTION_KEY = "firebase_analytics_collection_enabled"
        const val IOS_COLLECTION_KEY = "FIREBASE_ANALYTICS_COLLECTION_ENABLED"
        val XML_COMMENTS = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)
        val ANDROID_PRIVACY_KEYS =
            listOf(
                "google_analytics_adid_collection_enabled",
                "google_analytics_default_allow_ad_personalization_signals",
                "google_analytics_automatic_screen_reporting_enabled",
            )
        val IOS_PRIVACY_KEYS =
            listOf(
                "GOOGLE_ANALYTICS_IDFV_COLLECTION_ENABLED",
                "GOOGLE_ANALYTICS_DEFAULT_ALLOW_AD_PERSONALIZATION_SIGNALS",
                "FirebaseAutomaticScreenReportingEnabled",
            )
        val ADVERTISING_PERMISSIONS =
            listOf(
                "com.google.android.gms.permission.AD_ID",
                "android.permission.ACCESS_ADSERVICES_AD_ID",
                "android.permission.ACCESS_ADSERVICES_ATTRIBUTION",
                "android.permission.ACCESS_ADSERVICES_TOPICS",
            )
        val PUBLIC_CONSTRUCTOR_DISABLED = Regex("""constructor\(\)\s*:\s*this\(\s*enabled\s*=\s*false\s*,""")
    }
}
