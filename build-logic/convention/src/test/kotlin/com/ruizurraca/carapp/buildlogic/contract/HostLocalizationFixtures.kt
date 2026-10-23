package com.ruizurraca.carapp.buildlogic.contract

import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A synthetic Android source path for fixtures; no such file exists in the repository. */
internal const val ANDROID_FIXTURE = "androidApp/src/main/java/com/ruizurraca/carapp/LocalizationFixture.kt"

/** A synthetic iOS source path for fixtures; no such file exists in the repository. */
internal const val IOS_FIXTURE = "iosApp/LocalizationFixture.swift"

internal fun HostLocalizationInputs.withAndroidSource(path: String, text: String): HostLocalizationInputs =
    copy(androidMainSources = androidMainSources + (path to text))

internal fun HostLocalizationInputs.withIosSource(path: String, text: String): HostLocalizationInputs =
    copy(iosAppSources = iosAppSources + (path to text))

internal fun HostLocalizationInputs.withAndroidXml(path: String, text: String): HostLocalizationInputs =
    copy(androidResourceXml = androidResourceXml + (path to text))

internal fun HostLocalizationInputs.withoutIosSource(path: String): HostLocalizationInputs =
    copy(iosAppSources = iosAppSources - path)

/** Replaces [old], which must occur exactly once, so a fixture can never mutate nothing and pass vacuously. */
internal fun String.replacingExactlyOnce(old: String, new: String): String {
    val first = indexOf(old)
    check(first >= 0 && first == lastIndexOf(old)) { "fixture anchor must occur exactly once: $old" }
    return replaceRange(first, first + old.length, new)
}

/** Adds `<string name="[key]"[attributes]>[value]</string>` before `</resources>`. */
internal fun String.withAndroidString(key: String, value: String, attributes: String = ""): String =
    replacingExactlyOnce("</resources>", "    <string name=\"$key\"$attributes>$value</string>\n</resources>")

internal fun String.withoutAndroidString(key: String): String {
    val pattern = Regex("""\n\s*<string name="${Regex.escape(key)}"[^>]*>.*?</string>""")
    check(pattern.findAll(this).count() == 1) { "fixture anchor must occur exactly once: $key" }
    return replace(pattern, "")
}

internal fun String.withIosString(key: String, value: String): String = "$this\n\"$key\" = \"$value\";\n"

internal fun String.withoutIosString(key: String): String {
    val pattern = Regex("""\n"${Regex.escape(key)}"\s*=\s*"(?:[^"\\]|\\.)*";""")
    check(pattern.findAll(this).count() == 1) { "fixture anchor must occur exactly once: $key" }
    return replace(pattern, "")
}

internal fun assertRejected(expected: String, result: AssertionResult) {
    assertEquals(AssertionResult.Status.FAIL, result.status, result.detail)
    assertTrue(expected in result.detail, "expected the detail to contain <$expected> but was <${result.detail}>")
}

internal fun assertAccepted(result: AssertionResult) {
    assertEquals(AssertionResult.Status.PASS, result.status, result.detail)
}
