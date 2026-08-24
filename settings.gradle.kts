pluginManagement {
    // Convention plugins live in an included build so that E0-02 can configure every module from
    // one place without publishing anything.
    includeBuild("build-logic")

    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

// Type-safe project accessors (projects.core.model) instead of project(":core:model").
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "carApp"

include(":androidApp")
include(":shared")

// Core modules. E0-07 stages the final auth and sync contracts under D-55; their complete product
// behavior remains owned by the later auth and sync stories.
include(":core:model")
include(":core:common")
include(":core:analytics")
include(":core:crash")
include(":core:testing")
include(":core:database")
include(":core:auth")
include(":core:sync")

// E0-07 stages the final feature modules under D-55. Only its minimal Vehicle slice is functional;
// later feature stories complete these modules in place.
include(":feature:vehicle")
include(":feature:fuel")
include(":feature:session")

// Provider modules are a closed, explicit registry (D-44). A planned path does not become a
// Gradle project until its owning story creates the directory; filesystem-wide discovery is
// deliberately forbidden because it would admit unreviewed modules silently.
val firebaseProviderProjects = listOf(
    ":integration:firebase-auth",
    ":integration:firebase-firestore",
    ":integration:firebase-analytics",
    ":integration:firebase-crashlytics",
    ":wiring:firebase",
)
val excludeFirebaseProviders = providers
    .gradleProperty("carapp.excludeFirebaseProviders")
    .map(String::toBooleanStrict)
    .getOrElse(false)

firebaseProviderProjects
    .filter { path -> file(path.removePrefix(":").replace(':', '/')).isDirectory }
    .filterNot { excludeFirebaseProviders }
    .forEach(::include)
