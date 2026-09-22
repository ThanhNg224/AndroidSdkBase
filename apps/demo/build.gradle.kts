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
    implementation(project(":sdk:facades:otp-sdk"))
    implementation(project(":sdk:capabilities:otp-ui-compose"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    debugImplementation(libs.compose.tooling)
}
