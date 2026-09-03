plugins {
    id("carapp.kmp.library")
}

dependencies {
    "commonMainApi"(projects.core.common)
    "commonMainImplementation"(libs.kotlinx.serialization.json)
    "commonTestImplementation"(libs.kotlinx.coroutines.test)
}
