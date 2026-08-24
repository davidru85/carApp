plugins {
    id("carapp.kmp.library")
}

dependencies {
    "commonMainApi"(projects.core.model)
    "commonMainApi"(projects.core.common)
    "commonMainImplementation"(projects.core.auth)
}
