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

// Phase 0 core modules (docs/BACKLOG.md E0-03, E0-08). :core:auth, :core:database and
// :core:sync are introduced by their own later stories and MUST NOT appear here yet.
include(":core:model")
include(":core:common")
include(":core:crash")
include(":core:testing")