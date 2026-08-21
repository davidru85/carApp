// Root build file. It applies nothing: every module is configured by a convention plugin from the
// included build-logic build (E0-02), and versions live only in gradle/libs.versions.toml (E0-06).
//
// The plugins are declared here with `apply false` for one reason: it puts them on the build's
// plugin classpath so the convention plugins, which compile against them with `compileOnly`, can
// be instantiated. Declaring them once here also keeps the Kotlin plugin from being loaded twice
// with explicit versions, which breaks KMP builds.
plugins {
    // The architecture check inspects the whole module graph, so it is applied to the root project
    // only (E0-04). Its rules are generated from the dependency table of docs/TECHNICAL_PLAN.md §4.
    id("carapp.architecture")

    // contract-check asserts the repository invariants of docs/CONTRACTS.md §18 (E0-05).
    id("carapp.contract")

    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.skie) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kover) apply false
}
