// MUST be com.android.build.api.dsl.LibraryExtension, NOT com.android.build.gradle.LibraryExtension.
// Same reason as sdkbase.android.library.gradle.kts: under AGP 9's default `android.newDsl=true` the
// old type is not registered as an extension at all, and `extensions.configure<...>` fails with
// "Extension of type 'LibraryExtension' does not exist". The old type is removed in AGP 10.
import com.android.build.api.dsl.LibraryExtension
import org.gradle.accessors.dm.LibrariesForLibs

// Applied ONLY by UI modules. Keeping Compose out of `sdkbase.android.library` is the whole point:
// a host that wants only the engine must not inherit the Compose runtime.
//
// AGP 9's built-in Kotlin does NOT supply the Compose compiler. `buildFeatures.compose = true`
// without this plugin fails configuration with "Starting in Kotlin 2.0, the Compose Compiler
// Gradle plugin is required when compose is enabled." Verified. The plugin is put on build-logic's
// `implementation` classpath (see build-logic/build.gradle.kts) so it can be applied here by id.
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}

val libs = the<LibrariesForLibs>()

extensions.configure<LibraryExtension> {
    buildFeatures {
        compose = true
    }
    // The UI module DOES need resources; `buildFeatures.androidResources` is deprecated (removed in
    // AGP 10), so re-enable it through the current API.
    androidResources {
        enable = true
    }
}

dependencies {
    "implementation"(platform(libs.compose.bom))
    "api"(libs.compose.ui)
    "api"(libs.compose.material3)
    "implementation"(libs.compose.ui.graphics)
    "implementation"(libs.compose.tooling.preview)
    "debugImplementation"(libs.compose.tooling)
}
