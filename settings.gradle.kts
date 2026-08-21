// settings.gradle.kts  ← REPLACE existing file (root of project)
// REPO PATH: settings.gradle.kts
//
// Change vs old: added maven("https://jitpack.io") inside repositories block
// Required for: youtubedl-android + aria2c

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        google()
        mavenCentral()
        // ── Required for youtubedl-android ──────────────────────────────
        maven("https://jitpack.io")
    }
}

rootProject.name = "LinkShield Sandbox"

include(":app")
