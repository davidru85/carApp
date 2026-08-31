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
            export(project(":feature:vehicle"))
            export(project(":core:common"))
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":shared"))
            api(project(":feature:vehicle"))
            api(project(":core:common"))
            implementation(project(":wiring:firebase"))
        }
    }
}
