plugins {
    id("carapp.kmp.library")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":shared"))
                implementation(project(":core:testing"))
            }
        }
    }
}
