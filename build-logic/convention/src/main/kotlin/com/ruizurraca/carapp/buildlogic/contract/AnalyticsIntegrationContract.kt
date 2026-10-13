package com.ruizurraca.carapp.buildlogic.contract

import java.io.File

/**
 * Contract for the Firebase Analytics integration of `E3-09`.
 *
 * Three rules are asserted here because no other check can see them:
 *
 * - `docs/CONTRACTS.md §11.6` and `D-179`: the Firebase Analytics implementation is named only by
 *   `:wiring:firebase`. The architecture checker enforces that for the `:integration:*` package
 *   prefix; this asserts it for the public class name as well, because a platform host could reach
 *   the implementation through a re-export or a fully-qualified name that carries no package prefix
 *   on the offending line.
 * - `docs/adr/0066`, `D-65`: the iOS build links the `FirebaseAnalytics` SwiftPM product. GitLive
 *   2.6.0 does not supply Firebase Apple dependencies transitively, so the cinterop klib the
 *   Kotlin/Native compiler consumes resolves to `framework 'FirebaseAnalytics' not found` unless the
 *   host app links the product. Nothing else in the repository can observe that coupling: the Kotlin
 *   build succeeds either way and the failure appears only in the Xcode link step.
 * - `docs/CONTRACTS.md §16.1`: the provider SDK initialises at process start, before any
 *   `AnalyticsTracker` exists, so each host disables collection in its platform configuration.
 *   Without it a fresh install collects the provider's automatic events before the tracker can
 *   command the provider off.
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

    fun validate(): List<AssertionResult> = listOf(namingResult(), iosProductResult(), hostCollectionResult())

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
        if (!inputs.projectSpec.contains("product: $IOS_PRODUCT")) {
            problems += "iosApp/project.yml does not link the $IOS_PRODUCT SwiftPM product"
        }
        if (!inputs.xcodeProject.contains(IOS_PRODUCT)) {
            problems += "the generated Xcode project does not reference $IOS_PRODUCT"
        }
        return result(
            id = 38,
            name = "the iOS app links the FirebaseAnalytics SwiftPM product its cinterop requires",
            problems = problems,
        )
    }

    private fun hostCollectionResult(): AssertionResult {
        val problems = mutableListOf<String>()
        if (!ANDROID_COLLECTION_DISABLED.containsMatchIn(inputs.androidManifest)) {
            problems += "$ANDROID_MANIFEST_PATH does not set $ANDROID_COLLECTION_KEY to false"
        }
        if (!IOS_COLLECTION_DISABLED.containsMatchIn(inputs.iosInfoPlist)) {
            problems += "$IOS_INFO_PLIST_PATH does not set $IOS_COLLECTION_KEY to false"
        }
        return result(
            id = 39,
            name = "both hosts disable Firebase Analytics collection before the app graph exists",
            problems = problems,
        )
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
        const val IOS_PRODUCT = "FirebaseAnalytics"
        const val TRACKER_PATH =
            "integration/firebase-analytics/src/commonMain/kotlin/com/ruizurraca/carapp/" +
                "integration/firebase/analytics/FirebaseAnalyticsTracker.kt"
        const val WIRING_PATH =
            "wiring/firebase/src/commonMain/kotlin/com/ruizurraca/carapp/wiring/firebase/FirebaseAppProviders.kt"
        const val ANDROID_MANIFEST_PATH = "androidApp/src/main/AndroidManifest.xml"
        const val IOS_INFO_PLIST_PATH = "iosApp/Info.plist"
        const val ANDROID_COLLECTION_KEY = "firebase_analytics_collection_enabled"
        const val IOS_COLLECTION_KEY = "FIREBASE_ANALYTICS_COLLECTION_ENABLED"
        val ANDROID_COLLECTION_DISABLED =
            Regex("""<meta-data\s+android:name="$ANDROID_COLLECTION_KEY"\s+android:value="false"\s*/>""")
        val IOS_COLLECTION_DISABLED = Regex("""<key>$IOS_COLLECTION_KEY</key>\s*<false\s*/>""")
        val PUBLIC_CONSTRUCTOR_DISABLED = Regex("""constructor\(\)\s*:\s*this\(\s*enabled\s*=\s*false\s*,""")
    }
}
