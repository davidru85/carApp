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

tasks.test {
    systemProperty("carapp.repoRoot", rootProject.projectDir.parentFile.absolutePath)
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
