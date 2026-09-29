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

include(":sdk:core")
include(":sdk:core-testing")
include(":sdk:core-ui-compose")
include(":sdk:features:otp")
include(":sdk:features:otp-ui-compose")
include(":sdk:features:event-logging")
include(":sdk:features:logging-file")
include(":sdk:adapters:event-logging-work")
include(":sdk:adapters:otp-fake-sms")
include(":sdk:vendor:fake-sms-vendor")
include(":sdk:bom")
include(":apps:demo")
