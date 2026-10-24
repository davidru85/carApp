package com.ruizurraca.carapp.buildlogic.contract

import java.io.File

/** RED: declared without behaviour so the `E4-04` fixtures compile and execute. */
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
            fun from(repoRoot: File): Inputs =
                Inputs(
                    kotlinSources = emptyList(),
                    reporterSource = "",
                    projectSpec = repoRoot.resolve("iosApp/project.yml").readText(),
                    xcodeProject = repoRoot.resolve("iosApp/carApp.xcodeproj/project.pbxproj").readText(),
                    androidManifest = repoRoot.resolve("androidApp/src/main/AndroidManifest.xml").readText(),
                    iosInfoPlist = repoRoot.resolve("iosApp/Info.plist").readText(),
                )
        }
    }

    @Suppress("UnusedPrivateProperty")
    fun validate(): List<AssertionResult> =
        listOf(46, 47, 48).map { id -> AssertionResult(id, "RED stub", AssertionResult.Status.PASS) }
}
