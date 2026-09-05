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
// GuardedRepositoryInputsTest fails when a guard starts reading a file this list does not cover.
val guardedRepositoryInputs =
    fileTree(rootProject.projectDir.parentFile) {
        include(
            ".github/workflows/**",
            ".gitignore",
            "AGENTS.md",
            "androidApp/build.gradle.kts",
            "androidApp/src/**",
            "build-logic/convention/build.gradle.kts",
            "build.gradle.kts",
            "composition/ios/build.gradle.kts",
            "docs/**/*.md",
            "firebase.json",
            "functions/package.json",
            "gradle/libs.versions.toml",
            "iosApp/**",
            "scripts/**",
            "settings.gradle.kts",
            "shared/build.gradle.kts",
        )
        exclude(
            "**/build/**",
            "**/xcuserdata/**",
            "**/*.xcuserstate",
        )
    }

tasks.test {
    systemProperty("carapp.repoRoot", rootProject.projectDir.parentFile.absolutePath)
    inputs
        .files(guardedRepositoryInputs)
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
