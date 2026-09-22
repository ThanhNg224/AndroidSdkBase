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
    compileOnly(libs.dokka.gradle.plugin)
    compileOnly(libs.mavenpublish.gradle.plugin)

    // Unlike the plugins above, this one is actually APPLIED (via `plugins { id(...) }`) inside a
    // precompiled script plugin (sdkbase.android.compose.gradle.kts), not just referenced for its
    // Gradle-DSL types — so it must be on the runtime classpath, not merely compileOnly.
    implementation(libs.compose.compiler.gradle.plugin)

    // Reads kotlin.Metadata to recover real Kotlin visibility for the ABI dump (sdkbase.abi's
    // KotlinVisibility.kt). Used at task execution time, so `implementation`, not `compileOnly`.
    implementation(libs.kotlin.metadata.jvm)

    // Gradle generates `LibrariesForLibs` for this project's own build script use of `libs.*`,
    // but does not put that generated jar on the classpath used to compile the precompiled
    // script plugins under src/main/kotlin. Without this, `the<LibrariesForLibs>()` in those
    // plugins fails with "Unresolved reference 'accessors'". This is the documented workaround.
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}
