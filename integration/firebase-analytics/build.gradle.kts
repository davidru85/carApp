plugins {
    id("carapp.kmp.library")
}

dependencies {
    "commonMainApi"(projects.core.analytics)
    "commonMainImplementation"(libs.gitlive.firebase.analytics)
    // The native Firebase artifacts arrive transitively through the GitLive Android variant, whose
    // own BOM import the project platform raises to the pinned version, exactly as
    // :integration:firebase-auth and :integration:firebase-firestore already do.
    "androidMainImplementation"(platform(libs.firebase.bom))
    "commonTestImplementation"(libs.kotlinx.coroutines.test)
}
