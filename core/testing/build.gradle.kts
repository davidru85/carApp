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
    // `InMemoryRemoteSyncSource` rewrites the server-owned `updatedAt` field the way the provider
    // does, which requires reading the pushed payload as JSON.
    "commonMainImplementation"(libs.kotlinx.serialization.json)
    "commonTestImplementation"(libs.kotlinx.coroutines.test)
}

val sqliteVersion = libs.versions.sqlite.get()
configurations.matching { it.name == "androidHostTestRuntimeClasspath" }.configureEach {
    resolutionStrategy.dependencySubstitution {
        substitute(module("androidx.sqlite:sqlite-bundled:$sqliteVersion"))
            .using(module("androidx.sqlite:sqlite-bundled-jvm:$sqliteVersion"))
    }
}
