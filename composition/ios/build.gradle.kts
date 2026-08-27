plugins {
    id("carapp.kmp.library")
    id("carapp.skie")
}

kotlin {
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
            export(project(":shared"))
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":shared"))
            implementation(project(":wiring:firebase"))
        }
    }
}
