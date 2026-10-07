pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com[.]android.*")
                includeGroupByRegex("com[.]google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Blackjack"

// Two modules, on purpose:
//  :engine  - pure Kotlin, no Android. All the blackjack rules, strategy and maths live here
//             and are covered by fast JVM unit tests.
//  :app     - the Android UI (Jetpack Compose). It only renders engine state and forwards taps.
include(":engine")
include(":app")
