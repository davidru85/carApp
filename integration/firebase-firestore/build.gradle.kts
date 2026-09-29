plugins {
    id("carapp.kmp.library")
}

dependencies {
    "commonMainApi"(projects.core.sync)
    "commonMainImplementation"(libs.gitlive.firebase.auth)
    "commonMainImplementation"(libs.gitlive.firebase.firestore)
    "commonMainImplementation"(libs.kotlinx.serialization.json)
    "androidMainImplementation"(platform(libs.firebase.bom))
    "commonTestImplementation"(libs.kotlinx.coroutines.test)
    // The `E3-19` end-to-end test drives the real engine over the bundled in-memory SQLite database.
    "commonTestImplementation"(projects.core.database)
}
