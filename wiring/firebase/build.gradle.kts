plugins {
    id("carapp.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":shared"))
            implementation(project(":integration:firebase-auth"))
            implementation(project(":integration:firebase-firestore"))
        }
    }
}
