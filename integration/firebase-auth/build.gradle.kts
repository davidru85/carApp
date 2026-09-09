plugins {
    id("carapp.kmp.library")
}

dependencies {
    "commonMainApi"(projects.core.auth)
    "commonMainImplementation"(libs.gitlive.firebase.auth)
    "commonMainImplementation"(libs.gitlive.firebase.functions)
    "androidMainImplementation"(platform(libs.firebase.bom))
    "commonTestImplementation"(libs.kotlinx.coroutines.test)
}
