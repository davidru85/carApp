plugins {
    `kotlin-dsl`
}

// The plugin artifacts are compileOnly: the convention plugins only need their types to compile
// and apply them by id at execution time, so they are not leaked as transitive dependencies.
dependencies {
    compileOnly(libs.plugin.android.gradle)
    compileOnly(libs.plugin.kotlin.gradle)
    compileOnly(libs.plugin.skie.gradle)
    compileOnly(libs.plugin.ksp.gradle)
    compileOnly(libs.plugin.sqldelight.gradle)
    compileOnly(libs.plugin.ktlint.gradle)
    compileOnly(libs.plugin.detekt.gradle)
    compileOnly(libs.plugin.kover.gradle)
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(gradleTestKit())
}

// The guards in src/test read committed repository files through carapp.repoRoot. Gradle cannot
// observe those reads, so they are declared as task inputs here (D-119). Without this the task
// reports UP-TO-DATE after the very configuration it guards has changed, and a repository that
// violates an accepted decision looks green locally while CI fails.
//
// The declaration is not an allowlist: guards resolve paths from constants, compose them from other
// constants and walk directories, including one that walks the whole repository, so an enumerated
// include list cannot see what they actually read. The repository is declared and only generated
// output, tooling state and machine-local files are excluded.
val guardedRepositoryInputs =
    fileTree(rootProject.projectDir.parentFile) {
        exclude(
            "**/build/**",
            "**/.gradle/**",
            "**/.git/**",
            "**/.kotlin/**",
            "**/node_modules/**",
            "**/DerivedData/**",
            "**/xcuserdata/**",
            "**/*.xcuserstate",
            "**/*.log",
            "functions/lib/**",
            "iosApp/Local.xcconfig",
            "local.properties",
        )
    }

// Committed files that live inside a generated directory are named individually instead of widening
// the exclusions above. The Objective-C golden header is committed and is read by a guard.
val committedGeneratedInputs =
    files(
        rootProject.projectDir.parentFile.resolve("shared/build/generated/objc-header/Shared.h.golden"),
    )

tasks.test {
    systemProperty("carapp.repoRoot", rootProject.projectDir.parentFile.absolutePath)
    inputs
        .files(guardedRepositoryInputs, committedGeneratedInputs)
        .withPropertyName("guardedRepositoryInputs")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

kotlin {
    jvmToolchain(libs.versions.jdk.get().toInt())
}

gradlePlugin {
    plugins {
        register("kmpLibrary") {
            id = "carapp.kmp.library"
            implementationClass = "com.ruizurraca.carapp.buildlogic.KmpLibraryConventionPlugin"
        }
        register("androidApplication") {
            id = "carapp.android.application"
            implementationClass = "com.ruizurraca.carapp.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("compose") {
            id = "carapp.compose"
            implementationClass = "com.ruizurraca.carapp.buildlogic.ComposeConventionPlugin"
        }
        register("skie") {
            id = "carapp.skie"
            implementationClass = "com.ruizurraca.carapp.buildlogic.SkieConventionPlugin"
        }
        register("architecture") {
            id = "carapp.architecture"
            implementationClass = "com.ruizurraca.carapp.buildlogic.architecture.ArchitectureCheckPlugin"
        }
        register("quality") {
            id = "carapp.quality"
            implementationClass = "com.ruizurraca.carapp.buildlogic.QualityConventionPlugin"
        }
        register("coverage") {
            id = "carapp.coverage"
            implementationClass = "com.ruizurraca.carapp.buildlogic.CoverageConventionPlugin"
        }
        register("contract") {
            id = "carapp.contract"
            implementationClass = "com.ruizurraca.carapp.buildlogic.contract.ContractCheckPlugin"
        }
        register("sqldelight") {
            id = "carapp.sqldelight"
            implementationClass = "com.ruizurraca.carapp.buildlogic.SqlDelightConventionPlugin"
        }
    }
}
