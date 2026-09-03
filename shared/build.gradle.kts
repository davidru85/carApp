plugins {
    id("carapp.kmp.library")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlinx.datetime)
                implementation(libs.kotlinx.serialization.json)
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
                implementation(project(":core:testing"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        iosTest {
            kotlin.srcDir(
                rootProject.file(
                    "composition/ios/src/iosMain/kotlin/com/ruizurraca/carapp/locale",
                ),
            )
        }
    }
}
