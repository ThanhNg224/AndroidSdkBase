plugins {
    `kotlin-dsl`
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Convention plugins configure the AGP and Kotlin DSLs, so they compile against them.
    compileOnly(libs.agp.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)

    // Dokka, maven-publish and compose-compiler are actually applied via `plugins { id(...) }` inside precompiled scripts, so they must be on the runtime classpath.
    implementation(libs.dokka.gradle.plugin)
    implementation(libs.mavenpublish.gradle.plugin)
    implementation(libs.compose.compiler.gradle.plugin)

    // Only the API surface is needed to compile AbiTasks.kt; the real engine is resolved into a worker classloader at execution time.
    compileOnly(libs.abi.tools.api)

    // Puts the generated `LibrariesForLibs` accessor jar on the classpath so precompiled scripts can use `libs.*`.
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}
