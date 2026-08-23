plugins {
    id("carapp.kmp.library")
    id("carapp.skie")
}

kotlin {
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            // The canonical SPM module name consumed from Swift as `import Shared`
            // (docs/identifiers.md).
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlinx.datetime)
            }
        }
    }
}
