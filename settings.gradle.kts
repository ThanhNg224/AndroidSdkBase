pluginManagement {
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

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "AndroidSdkBase"

// The topology registry is the single source for SDK module inclusion and zone ownership.
apply(from = "gradle/module-topology.gradle.kts")

@Suppress("UNCHECKED_CAST")
val registeredModules = (extra["zones"] as Map<String, List<String>>).values.flatten().distinct()
registeredModules.forEach { modulePath ->
    val moduleDirectory = modulePath.removePrefix(":").replace(":", "/")
    if (file("$moduleDirectory/build.gradle.kts").isFile) include(modulePath)
}

// Verification scripts may append explicit, temporary includes for unregistered guard fixtures.
