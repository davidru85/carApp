plugins {
    id("carapp.kmp.library")
    id("carapp.sqldelight")
}

dependencies {
    "commonMainApi"(projects.core.model)
    "commonMainImplementation"(projects.core.common)
    "commonTestImplementation"(libs.kotlinx.coroutines.test)
}
