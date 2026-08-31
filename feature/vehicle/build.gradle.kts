plugins {
    id("carapp.kmp.library")
}

dependencies {
    "commonMainApi"(projects.core.model)
    "commonMainApi"(projects.core.common)
    "commonMainImplementation"(projects.core.database)
    "commonMainImplementation"(projects.core.sync)
    "commonMainImplementation"(libs.kotlinx.serialization.json)
    "commonTestImplementation"(projects.core.testing)
    "commonTestImplementation"(libs.kotlinx.coroutines.test)
    "commonTestImplementation"(libs.turbine)
    "androidHostTestImplementation"(libs.konsist)
}
