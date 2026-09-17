plugins {
    id("carapp.kmp.library")
}

dependencies {
    "commonMainApi"(projects.core.model)
    "commonMainApi"(projects.core.common)
    // `createSyncController(...)` exposes `SyncDatabaseAccess` in its public parameter list, so the
    // module's public API is only self-contained while this edge is `api` rather than `implementation`.
    "commonMainApi"(projects.core.database)
    "commonMainImplementation"(libs.kotlinx.serialization.json)
    "commonTestImplementation"(libs.kotlinx.coroutines.test)
    "commonTestImplementation"(libs.sqldelight.runtime)
    "commonTestImplementation"(libs.sqldelight.async.extensions)
    "commonTestImplementation"(libs.sqldelight.androidx.driver)
    "commonTestImplementation"(libs.androidx.sqlite.bundled)
}
