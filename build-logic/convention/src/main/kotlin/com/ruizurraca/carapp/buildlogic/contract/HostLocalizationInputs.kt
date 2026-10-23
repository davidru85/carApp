package com.ruizurraca.carapp.buildlogic.contract

import java.io.File

/**
 * The committed sources the `E4-02` localization assertions 41, 42 and 44 read.
 *
 * Every path is repository-relative with `/` separators, so a failure names the file a reviewer opens.
 * Fixture tests replace one member with a mutated copy; [from] reads the real repository.
 */
internal data class HostLocalizationInputs(
    val contract: String,
    val appErrorSource: String,
    val producerSources: Map<String, String>,
    val androidMainSources: Map<String, String>,
    val androidResourceXml: Map<String, String>,
    val iosAppSources: Map<String, String>,
    val androidMappingTest: String,
    val iosMappingTest: String,
    val iosStringsEn: String,
    val iosStringsEs: String,
) {
    val androidStringsEn: String get() = androidResourceXml[ANDROID_STRINGS_EN].orEmpty()
    val androidStringsEs: String get() = androidResourceXml[ANDROID_STRINGS_ES].orEmpty()

    companion object {
        const val CONTRACTS = "docs/CONTRACTS.md"
        const val ANDROID_SOURCE_ROOT = "androidApp/src/main/java"
        const val ANDROID_RESOURCE_ROOT = "androidApp/src/main"
        const val ANDROID_STRINGS_EN = "androidApp/src/main/res/values/strings.xml"
        const val ANDROID_STRINGS_ES = "androidApp/src/main/res/values-es/strings.xml"
        const val IOS_SOURCE_ROOT = "iosApp"
        const val IOS_STRINGS_EN = "iosApp/en.lproj/Localizable.strings"
        const val IOS_STRINGS_ES = "iosApp/es.lproj/Localizable.strings"
        const val ANDROID_MAPPING = "androidApp/src/main/java/com/ruizurraca/carapp/UiMessageStrings.kt"
        const val IOS_MAPPING = "iosApp/UiMessageMapping.swift"
        const val ANDROID_MAPPING_TEST = "androidApp/src/test/java/com/ruizurraca/carapp/UiMessageStringsTest.kt"
        const val IOS_MAPPING_TEST = "iosApp/Tests/UiMessageMappingTests.swift"

        fun from(repoRoot: File): HostLocalizationInputs =
            HostLocalizationInputs(
                contract = repoRoot.resolve(CONTRACTS).readText(),
                appErrorSource = repoRoot.resolve(UiMessageCodeInventory.APP_ERROR_PATH).readText(),
                producerSources = UiMessageCodeInventory.producerSources(repoRoot),
                androidMainSources = sourcesUnder(repoRoot, ANDROID_SOURCE_ROOT, "kt", recursive = true),
                androidResourceXml = sourcesUnder(repoRoot, ANDROID_RESOURCE_ROOT, "xml", recursive = true),
                iosAppSources = sourcesUnder(repoRoot, IOS_SOURCE_ROOT, "swift", recursive = false),
                androidMappingTest = textOrEmpty(repoRoot, ANDROID_MAPPING_TEST),
                iosMappingTest = textOrEmpty(repoRoot, IOS_MAPPING_TEST),
                iosStringsEn = textOrEmpty(repoRoot, IOS_STRINGS_EN),
                iosStringsEs = textOrEmpty(repoRoot, IOS_STRINGS_ES),
            )

        /**
         * `path -> text` of every file of the given extension under [directory], sorted by path, build
         * output excluded. The iOS app target is the top-level Swift files of `iosApp`, so iOS is read
         * non-recursively and its `Tests` and `UITests` directories are not app sources. The extension
         * and the file patterns are never written with a slash-star, which Kotlin reads as a nested
         * comment.
         */
        private fun sourcesUnder(
            repoRoot: File,
            directory: String,
            extension: String,
            recursive: Boolean,
        ): Map<String, String> {
            val base = repoRoot.resolve(directory)
            if (!base.isDirectory) return emptyMap()
            val files = if (recursive) base.walkTopDown().toList() else base.listFiles().orEmpty().toList()
            return files
                .filter { file ->
                    file.isFile &&
                        file.extension == extension &&
                        "${File.separator}build${File.separator}" !in file.path
                }.map { file -> UiMessageCodeInventory.relativePath(repoRoot, file) to file.readText() }
                .sortedBy { (path, _) -> path }
                .toMap()
        }

        private fun textOrEmpty(repoRoot: File, path: String): String =
            repoRoot.resolve(path).takeIf(File::isFile)?.readText().orEmpty()
    }
}
