plugins {
    `kotlin-dsl`
}

// The plugin artifacts are compileOnly: the convention plugins only need their types to compile
// and apply them by id at execution time, so they are not leaked as transitive dependencies.
dependencies {
    compileOnly(libs.plugin.android.gradle)
    compileOnly(libs.plugin.kotlin.gradle)
    compileOnly(libs.plugin.skie.gradle)
    compileOnly(libs.plugin.ksp.gradle)
    compileOnly(libs.plugin.room.gradle)
}

kotlin {
    jvmToolchain(libs.versions.jdk.get().toInt())
}

gradlePlugin {
    plugins {
        register("kmpLibrary") {
            id = "carapp.kmp.library"
            implementationClass = "com.ruizurraca.carapp.buildlogic.KmpLibraryConventionPlugin"
        }
        register("androidApplication") {
            id = "carapp.android.application"
            implementationClass = "com.ruizurraca.carapp.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("compose") {
            id = "carapp.compose"
            implementationClass = "com.ruizurraca.carapp.buildlogic.ComposeConventionPlugin"
        }
        register("skie") {
            id = "carapp.skie"
            implementationClass = "com.ruizurraca.carapp.buildlogic.SkieConventionPlugin"
        }
        register("room") {
            id = "carapp.room"
            implementationClass = "com.ruizurraca.carapp.buildlogic.RoomConventionPlugin"
        }
    }
}
