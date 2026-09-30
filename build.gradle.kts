// AGP 9.4.1 ships KGP 2.2.10. This classpath entry is the documented, supported way to move to a
// newer Kotlin: Gradle resolves the conflict upward (verified: 2.2.10 -> 2.4.20).
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
    }
}

plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.maven.publish) apply false
    alias(libs.plugins.spotless)
    id("sdkbase.error-catalog")
    id("sdkbase.zone-guard")
}

apply(from = "gradle/module-topology.gradle.kts")

spotless {
    // Filter within each tree so configuration-cache inputs exclude build outputs.
    kotlin {
        target(
            fileTree(rootDir) {
                include("sdk/**/*.kt", "apps/**/*.kt", "build-logic/src/**/*.kt")
                exclude("**/build/**", "verification/**")
            },
        )
        ktlint(libs.versions.ktlint.get()).setEditorConfigPath(file(".editorconfig").path)
    }
    kotlinGradle {
        target(
            fileTree(rootDir) {
                include("**/*.gradle.kts")
                exclude("**/build/**", "verification/**", ".git/**", ".gradle/**")
            },
        )
        ktlint(libs.versions.ktlint.get()).setEditorConfigPath(file(".editorconfig").path)
    }
}

subprojects {
    group = providers.gradleProperty("sdkbase.group").get()
    version = providers.gradleProperty("sdkbase.version").get()
}

tasks.named<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}

// Spotless applies the base plugin, which supplies root clean/check tasks. Unqualified `check`
// also selects every subproject's check; add build-logic tests and the error catalog at the root.
tasks.named("check") {
    group = "verification"
    description = "Runs formatting, build-logic tests and the error catalog alongside every module's check."
    dependsOn(gradle.includedBuild("build-logic").task(":test"))
    dependsOn("checkErrorCatalog")
    dependsOn("spotlessCheck")
}
