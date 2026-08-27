plugins {
    id("carapp.kmp.library")
}

dependencies {
    "commonMainApi"(projects.core.common)
    "commonMainApi"(projects.core.crash)
    "commonMainApi"(projects.core.analytics)
    "commonMainApi"(projects.core.database)
    "commonMainApi"(projects.core.auth)
    "commonMainApi"(projects.core.sync)
    "commonMainImplementation"(libs.sqldelight.runtime)
    "commonMainImplementation"(libs.sqldelight.androidx.driver)
    "commonMainImplementation"(libs.androidx.sqlite.bundled)
    "commonTestImplementation"(libs.kotlinx.coroutines.test)
}

val sqliteVersion = libs.versions.sqlite.get()
configurations.matching { it.name == "androidHostTestRuntimeClasspath" }.configureEach {
    resolutionStrategy.dependencySubstitution {
        substitute(module("androidx.sqlite:sqlite-bundled:$sqliteVersion"))
            .using(module("androidx.sqlite:sqlite-bundled-jvm:$sqliteVersion"))
    }
}
