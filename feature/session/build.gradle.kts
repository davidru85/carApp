plugins {
    id("carapp.kmp.library")
}

dependencies {
    "commonMainApi"(projects.core.model)
    "commonMainApi"(projects.core.common)
    "commonMainImplementation"(projects.core.auth)
    "commonMainImplementation"(projects.core.database)
    "commonTestImplementation"(projects.core.testing)
    "commonTestImplementation"(libs.kotlinx.coroutines.test)
    "commonTestImplementation"(libs.turbine)
}
