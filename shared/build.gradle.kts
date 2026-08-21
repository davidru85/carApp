plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.skie)
}

kotlin {
    // Pinned in gradle/libs.versions.toml (E0-06); drives the Kotlin jvmTarget for androidTarget.
    jvmToolchain(libs.versions.jdk.get().toInt())

    androidLibrary {
        // Derived from the module path per docs/identifiers.md, "Module Android namespaces".
        // It is a build identifier, not the Kotlin package root: shared code stays in
        // com.ruizurraca.carapp. E0-02 computes this from the Gradle project path so that no
        // module build script carries the literal.
        namespace = "com.ruizurraca.carapp.shared"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()

        // The AGP KMP library plugin creates no host test runner by default. Without this the
        // common tests would only ever execute on the Kotlin/Native targets.
        withHostTestBuilder {}
    }
    iosX64 {
        binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }
    iosArm64 {
        binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }
    iosSimulatorArm64 {
        binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.datetime)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
    }
}
