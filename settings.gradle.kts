pluginManagement {
    includeBuild("gradle/build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven(url = "https://www.jitpack.io")
    }
}

dependencyResolutionManagement {
    versionCatalogs {
        create("mihonx") {
            from(files("gradle/mihon.versions.toml"))
        }
    }

    @Suppress("UnstableApiUsage")
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    @Suppress("UnstableApiUsage")
    repositories {
        google()
        mavenCentral()
        maven(url = "https://www.jitpack.io")
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "Mihon"

// Desktop (Linux) fork — Suwayomi-Server client.
// Only modules on the desktop dependency path are included; the Android-only
// modules below are set aside (recoverable via git) because they require the
// Android SDK to configure and are not used by the desktop build.
include(":desktop")

// Modules converted to Kotlin Multiplatform (jvm target) as the fork progresses:
// include(":i18n")
// include(":source-api")
// include(":domain")
// include(":presentation-core")
// include(":bridge-suwayomi")

// --- Android-only (excluded from the desktop fork) ---
// include(":app")
// include(":baseline-profile")
// include(":core-metadata")
// include(":core:archive")
// include(":core:common")
// include(":data")
// include(":presentation-widget")
// include(":source-local")
// include(":telemetry")
