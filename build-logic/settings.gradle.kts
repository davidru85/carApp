// Included build holding the convention plugins. It reads the same version catalog as the main
// build, so gradle/libs.versions.toml stays the single source of versions (E0-06).
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"

include(":convention")
