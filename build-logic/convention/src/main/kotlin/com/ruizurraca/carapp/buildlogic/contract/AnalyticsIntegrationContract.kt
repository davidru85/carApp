package com.ruizurraca.carapp.buildlogic.contract

import java.io.File

/**
 * Contract for the Firebase Analytics integration of `E3-09`.
 *
 * Two rules are asserted here because no other check can see them:
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
                )
            }
        }
    }

    fun validate(): List<AssertionResult> = listOf(namingResult(), iosProductResult())

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

        return result(
            id = 37,
            name = "the Firebase Analytics implementation is named only by :wiring:firebase and starts disabled",
            problems = offenders + missing + notDisabledByDefault,
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
    }
}
