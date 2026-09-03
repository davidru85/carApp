plugins {
    id("carapp.kmp.library")
}

dependencies {
    "commonMainApi"(projects.core.common)
    "commonTestImplementation"(libs.kotlinx.coroutines.test)
}
