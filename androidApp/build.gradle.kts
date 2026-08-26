plugins {
    id("carapp.android.application")
    id("carapp.compose")
    id("com.google.gms.google-services")
}

android {
    defaultConfig {
        versionCode = 1
        versionName = "0.0.1"
    }

    // Debug application id suffix so debug and release can coexist on one device (docs/identifiers.md).
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = false
        }
    }
}

val providerFreeBuild =
    providers
        .gradleProperty("carapp.excludeFirebaseProviders")
        .map(String::toBooleanStrict)
        .getOrElse(false)

dependencies {
    if (providerFreeBuild) {
        implementation(project(":shared"))
    } else {
        implementation(project(":wiring:firebase"))
    }

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.compose.activity)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.appcheck.playintegrity)
    debugImplementation(libs.firebase.appcheck.debug)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)
}
