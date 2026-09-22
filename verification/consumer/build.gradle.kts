// AGP 9.4.1 ships KGP 2.2.10 as its built-in Kotlin. The compose-compiler plugin version must match
// the actual Kotlin compiler in use, so this override mirrors the root build's documented, verified
// fix (Gradle resolves 2.2.10 -> 2.4.20) rather than leaving app/build.gradle.kts's compose compiler
// (2.4.20) mismatched against a 2.2.10 compiler.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
    }
}

plugins {
    id("com.android.application") version "9.4.1" apply false
    // Required since Kotlin 2.0 for any module with `buildFeatures.compose = true`. AGP 9's
    // built-in Kotlin does NOT cover this — see the root build's Global Constraints.
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
}
