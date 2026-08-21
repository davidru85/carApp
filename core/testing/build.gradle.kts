plugins {
    id("carapp.kmp.library")
}

dependencies {
    "commonMainApi"(projects.core.common)
    "commonMainApi"(projects.core.crash)
}
