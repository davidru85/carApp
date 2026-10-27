import java.io.File
import java.util.Properties

plugins {
    id("carapp.android.application")
    id("carapp.compose")
    id("com.google.gms.google-services")
    // D-239: injects the build ID the Crashlytics Android SDK reads at app start; applied after the
    // Google Services plugin, as Firebase documents.
    id("com.google.firebase.crashlytics")
}

// `D-257`: release signing comes from a properties file named by a Gradle property, because the
// repository is public and no keystore, password or keystore.properties may live in it. The property is
// read from `~/.gradle/gradle.properties`; when it is absent, or names a file that does not exist, the
// release simply stays unsigned, so CI and a fresh clone are unaffected.
//
// The file MUST lie outside the repository root. A path inside it fails the build, so a committed copy
// cannot become the signing source even by accident.
val releaseSigningPropertiesPath: String? =
    providers.gradleProperty("carapp.releaseSigningProperties").orNull?.takeIf(String::isNotBlank)

// A relative path resolves against the repository root, never against the Gradle daemon's working
// directory, so the in-repository check below cannot be evaded by how the property is written.
val releaseSigningFile: File? =
    releaseSigningPropertiesPath?.let { path ->
        val file = File(path)
        if (file.isAbsolute) file else rootProject.projectDir.resolve(path)
    }

if (releaseSigningFile != null) {
    // Checked whether or not the file exists: an in-repository path is refused as soon as it is named,
    // so a committed copy could never become the signing source even after someone creates it.
    val repositoryRoot = rootProject.projectDir.canonicalFile
    val signingFile = releaseSigningFile.canonicalFile
    check(!signingFile.startsWith(repositoryRoot)) {
        "carapp.releaseSigningProperties must resolve outside the repository: ${signingFile.path}"
    }
}

val releaseSigningProperties: Properties? =
    releaseSigningFile
        ?.takeIf(File::exists)
        ?.let { file ->
            Properties().apply { file.inputStream().use(::load) }
        }

android {
    defaultConfig {
        versionCode = 1
        versionName = "0.0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseSigningProperties != null) {
            create("release") {
                storeFile = File(releaseSigningProperties.getProperty("storeFile"))
                storePassword = releaseSigningProperties.getProperty("storePassword")
                keyAlias = releaseSigningProperties.getProperty("keyAlias")
                keyPassword = releaseSigningProperties.getProperty("keyPassword")
            }
        }
    }

    // Debug application id suffix so debug and release can coexist on one device (docs/identifiers.md).
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = false
            // `D-257`: signed only where the owner supplied the out-of-repository properties file.
            signingConfig = signingConfigs.findByName("release")
        }
        // `D-259`: release code and release settings against the development project, so a backend can
        // be exercised before `E4-10` creates production. It keeps the `.debug` identity because it is
        // never distributed, so it cannot coexist with a debug build on one device.
        create("staging") {
            initWith(getByName("release"))
            applicationIdSuffix = ".debug"
            signingConfig = signingConfigs.findByName("debug")
            matchingFallbacks += listOf("release")
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
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.compose.activity)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.appcheck.playintegrity)
    debugImplementation(libs.firebase.appcheck.debug)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(kotlin("test-junit"))

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.compose.ui.test.junit4.accessibility)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
}
