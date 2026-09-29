plugins {
    id("carapp.kmp.library")
}

dependencies {
    "commonMainApi"(projects.core.analytics)
    "commonMainImplementation"(libs.gitlive.firebase.analytics)
    "androidMainImplementation"(platform(libs.firebase.bom))
    "androidMainImplementation"(libs.firebase.analytics)
    "commonTestImplementation"(libs.kotlinx.coroutines.test)
}
