plugins {
    id("carapp.kmp.library")
}

dependencies {
    "commonMainApi"(projects.core.model)
    "commonMainImplementation"(libs.kotlinx.datetime)
}
