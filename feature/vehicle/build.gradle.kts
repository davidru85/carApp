plugins {
    id("carapp.kmp.library")
}

dependencies {
    "commonMainApi"(projects.core.model)
    "commonMainApi"(projects.core.common)
    "commonMainImplementation"(projects.core.database)
    "commonMainImplementation"(projects.core.sync)
    "commonMainImplementation"(libs.kotlinx.serialization.json)
    "commonMainImplementation"(libs.sqldelight.async.extensions)
    "commonMainImplementation"(libs.sqldelight.coroutines.extensions)
    "commonTestImplementation"(projects.core.testing)
    "commonTestImplementation"(libs.kotlinx.coroutines.test)
    "commonTestImplementation"(libs.turbine)
}
