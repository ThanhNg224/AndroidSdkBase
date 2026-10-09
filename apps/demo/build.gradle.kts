plugins {
    alias(libs.plugins.android.application)
    // Required since Kotlin 2.0 for any module with `buildFeatures.compose = true`.
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "io.github.thanhng224.sdkbase.demo"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "io.github.thanhng224.sdkbase.demo"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = providers.gradleProperty("sdkbase.version").get()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    bundle {
        language { enableSplit = false }
    }

    buildTypes {
        release {
            // The demo is also the shrinker canary: if the SDK's consumer rules are wrong,
            // this build is where it shows.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
    }
}

kotlin {
    jvmToolchain(libs.versions.javaToolchain.get().toInt())
    compilerOptions {
        allWarningsAsErrors.set(
            providers.gradleProperty("sdkbase.warningsAsErrors").map(String::toBoolean).getOrElse(false),
        )
    }
}

dependencies {
    // The app is not published; it compiles at the toolchain's own API level, not the floor.
    implementation(libs.kotlin.stdlib) {
        version { require(libs.versions.kotlin.get()) }
    }
    implementation(project(":sdk:features:otp"))
    implementation(project(":sdk:features:otp-ui-compose"))
    implementation(project(":sdk:core-ui-compose"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.core)
    debugImplementation(libs.compose.tooling)

    testImplementation(project(":sdk:core-testing"))
    testImplementation(libs.junit)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    debugImplementation(libs.compose.ui.test.manifest)
}
