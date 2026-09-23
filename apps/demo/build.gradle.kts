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
    }

    buildTypes {
        release {
            // The demo is also the shrinker canary: if the SDK's consumer rules are wrong,
            // this build is where it shows.
            isMinifyEnabled = true
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
}

kotlin {
    jvmToolchain(libs.versions.javaToolchain.get().toInt())
}

dependencies {
    implementation(project(":sdk:features:otp"))
    implementation(project(":sdk:features:otp-ui-compose"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    // Not in gradle/libs.versions.toml: this app-only dependency reuses the already-catalogued
    // androidxLifecycle version rather than adding a library entry to the shared catalog, which
    // is owned by the module-topology/publishing work in flight elsewhere in this repo. It must
    // never be added to a published SDK module — collectAsStateWithLifecycle() is a host/app
    // convenience, not part of the SDK's contract.
    implementation("androidx.lifecycle:lifecycle-runtime-compose:${libs.versions.androidxLifecycle.get()}")
    debugImplementation(libs.compose.tooling)
}
