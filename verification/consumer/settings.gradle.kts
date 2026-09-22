pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // The artifacts under test. Declared FIRST so a stale Central copy can never win.
        maven {
            name = "sdkLocalRepo"
            url = uri(providers.gradleProperty("sdkLocalRepo").get())
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "sdkbase-consumer"
include(":app")
