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

    // Unlike the two above, dokka and vanniktech maven-publish ARE actually applied (via
    // `plugins { id(...) }`) inside precompiled script plugins — dokka and mavenpublish inside
    // sdkbase.android.publishing.gradle.kts, and compose-compiler inside
    // sdkbase.android.compose.gradle.kts — so all three must be on the runtime classpath, not
    // merely compileOnly, the same reasoning documented for compose-compiler below.
    implementation(libs.dokka.gradle.plugin)
    implementation(libs.mavenpublish.gradle.plugin)
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
