import com.android.build.api.dsl.LibraryExtension
import org.gradle.accessors.dm.LibrariesForLibs
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

// AGP 9 supplies Kotlin itself. Applying `org.jetbrains.kotlin.android` here would fail the build.
plugins {
    id("com.android.library")
}

// Precompiled script plugins cannot use the generated `libs` accessor directly.
val libs = the<LibrariesForLibs>()

kotlin {
    // Strict: a declaration without a visibility modifier is a compile ERROR, not a warning.
    explicitApi()
    jvmToolchain(libs.versions.javaToolchain.get().toInt())

    compilerOptions {
        // `-Xjvm-default` is gone in Kotlin 2.4. `enable` keeps the DefaultImpls bridges that
        // Java hosts link against; `no-compatibility` would silently break them.
        freeCompilerArgs.add("-jvm-default=enable")

        // Emit metadata a host on older Kotlin can still read. See docs/COMPATIBILITY.md.
        val floor = KotlinVersion.fromVersion(libs.versions.kotlinStdlibFloor.get().substringBeforeLast('.'))
        languageVersion.set(floor)
        apiVersion.set(floor)

        // Warnings in a published SDK are future breakage. Treat them as errors in CI.
        allWarningsAsErrors.set(
            providers.gradleProperty("sdkbase.warningsAsErrors").map(String::toBoolean).getOrElse(false)
        )
    }
}

extensions.configure<LibraryExtension> {
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        consumerProguardFiles("consumer-rules.pro")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Every module prefixes its resources so it can never collide with a host's resources.
    // `sdk_` plus the module name, e.g. `sdk_otp_`.
    resourcePrefix = "sdk_" + project.name.replace('-', '_') + "_"

    buildTypes {
        release {
            // A library must NOT shrink itself: the host's R8 run does that, guided by
            // consumer-rules.pro. Shrinking here would strip API the host has not called yet.
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        // Off by default: it costs build time and widens the surface. A module opts in if it needs it.
        buildConfig = false
    }

    // `buildFeatures.androidResources` is deprecated and removed in AGP 10; this is the replacement.
    androidResources {
        enable = false
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
        // Lint this module only; dependencies are linted where they live.
        checkDependencies = false
    }
}

dependencies {
    "api"(libs.kotlin.stdlib)
    "implementation"(libs.androidx.annotation)
    "testImplementation"(libs.junit)
    "testImplementation"(libs.coroutines.test)
}

// Consumer rules must exist even when empty, otherwise the AAR silently ships none.
val consumerRules = layout.projectDirectory.file("consumer-rules.pro").asFile
if (!consumerRules.exists()) {
    consumerRules.parentFile.mkdirs()
    consumerRules.writeText(
        "# Keep rules this module needs its CONSUMER's R8 run to apply.\n" +
            "# Anything reflective, serialized, or reached only from the host belongs here.\n"
    )
}
