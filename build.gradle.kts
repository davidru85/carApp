// Root build file. It applies nothing: every module is configured by a convention plugin from the
// included build-logic build (E0-02), and versions live only in gradle/libs.versions.toml (E0-06).
//
// The plugins are declared here with `apply false` for one reason: it puts them on the build's
// plugin classpath so the convention plugins, which compile against them with `compileOnly`, can
// be instantiated. Declaring them once here also keeps the Kotlin plugin from being loaded twice
// with explicit versions, which breaks KMP builds.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.skie) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
}
