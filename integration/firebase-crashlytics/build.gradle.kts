plugins {
    id("carapp.kmp.library")
}

dependencies {
    "commonMainApi"(projects.core.crash)
    "commonMainImplementation"(libs.gitlive.firebase.crashlytics)
    // The native Firebase Crashlytics artifact arrives transitively through the GitLive Android
    // variant, whose own BOM import (34.17.0) the project platform raises to the pinned version,
    // exactly as the other three Firebase integrations already do.
    "androidMainImplementation"(platform(libs.firebase.bom))
}
