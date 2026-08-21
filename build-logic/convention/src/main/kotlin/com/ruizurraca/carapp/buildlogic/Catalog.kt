package com.ruizurraca.carapp.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

/**
 * Access to `gradle/libs.versions.toml`, which `E0-06` made the single source of every version.
 * A convention plugin MUST read versions through here and MUST NOT inline a literal.
 */
internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.version(alias: String): String =
    findVersion(alias).orElseThrow { IllegalStateException("Missing version '$alias' in libs.versions.toml") }
        .requiredVersion

internal fun VersionCatalog.intVersion(alias: String): Int = version(alias).toInt()

/**
 * The Android build namespace of a module, derived from its Gradle path per `docs/identifiers.md`
 * ("Module Android namespaces", decision `D-24`): the shared package root, then the module path
 * with `:` replaced by `.` and any `-` removed.
 *
 * This is why no module build script carries a namespace literal. It is a build identifier, not
 * the Kotlin package root, which stays `com.ruizurraca.carapp` for shared code.
 */
internal val Project.androidNamespace: String
    get() = ANDROID_NAMESPACE_ROOT + path.replace(":", ".").replace("-", "")

internal const val ANDROID_NAMESPACE_ROOT = "com.ruizurraca.carapp"
