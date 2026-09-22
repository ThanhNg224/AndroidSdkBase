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
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.maven.publish) apply false
    alias(libs.plugins.dependency.analysis)
}

// apply(from = "gradle/module-topology.gradle.kts") — restored in Task 2, once that file exists.

subprojects {
    group = providers.gradleProperty("sdkbase.group").get()
    version = providers.gradleProperty("sdkbase.version").get()
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
