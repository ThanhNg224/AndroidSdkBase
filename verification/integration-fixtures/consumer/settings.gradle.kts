pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri(providers.gradleProperty("fixtureRepo").get()) }
        google()
        mavenCentral()
    }
}
rootProject.name = "sdkbase-integration-consumer"
include(":direct", ":adapter")
