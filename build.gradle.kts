// Root build file. Plugin versions are declared in gradle/libs.versions.toml and applied
// in each module. The Kotlin Gradle plugin MUST NOT be loaded multiple times with
// explicit versions (Gradle warns and it can break KMP builds). The root declares the
// plugins without applying them, so subprojects apply them without a version.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.skie) apply false
    alias(libs.plugins.ksp) apply false
}