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
    }
}

dependencies {
    "testImplementation"(libs.junit)
    "testImplementation"(libs.coroutines.test)
}
