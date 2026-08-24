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
                api(projects.core.model)
                api(projects.core.common)
                api(projects.core.database)
                api(projects.core.auth)
                api(projects.core.sync)
                api(projects.core.analytics)
                api(projects.core.crash)
                api(projects.feature.vehicle)
                api(projects.feature.fuel)
                api(projects.feature.session)
            }
        }
        commonTest {
            dependencies {
                implementation(project(":shared:testing"))
            }
        }
    }
}
