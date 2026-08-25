plugins {
    id("carapp.kmp.library")
}

dependencies {
    "commonMainApi"(projects.core.sync)
    "commonMainImplementation"(libs.gitlive.firebase.firestore)
    "commonMainImplementation"(libs.kotlinx.serialization.json)
    "androidMainImplementation"(platform(libs.firebase.bom))
}
