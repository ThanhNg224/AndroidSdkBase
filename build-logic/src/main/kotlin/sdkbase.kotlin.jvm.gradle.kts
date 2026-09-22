import org.gradle.accessors.dm.LibrariesForLibs
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    id("org.jetbrains.kotlin.jvm")
}

val libs = the<LibrariesForLibs>()

kotlin {
    explicitApi()
    jvmToolchain(libs.versions.javaToolchain.get().toInt())
    compilerOptions {
        freeCompilerArgs.add("-jvm-default=enable")
        val floor = KotlinVersion.fromVersion(libs.versions.kotlinMetadataFloor.get())
        languageVersion.set(floor)
        apiVersion.set(floor)

        // Warnings in a published SDK are future breakage. Treat them as errors in CI.
        allWarningsAsErrors.set(
            providers.gradleProperty("sdkbase.warningsAsErrors").map(String::toBoolean).getOrElse(false)
        )
    }
}

dependencies {
    "testImplementation"(libs.junit)
    "testImplementation"(libs.coroutines.test)
}
