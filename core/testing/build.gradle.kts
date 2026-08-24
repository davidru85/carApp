plugins {
    id("carapp.kmp.library")
}

dependencies {
    "commonMainApi"(projects.core.common)
    "commonMainApi"(projects.core.crash)
    "commonMainApi"(projects.core.analytics)
    "commonMainApi"(projects.core.database)
    "commonMainApi"(projects.core.auth)
    "commonMainApi"(projects.core.sync)
}
