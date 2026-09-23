plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val sdkVersion: String = providers.gradleProperty("sdkVersion").get()

android {
    namespace = "io.github.thanhng224.consumer"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.thanhng224.consumer"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            // The shrinker canary: if the SDK's consumer rules are incomplete, this fails.
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
    jvmToolchain(17)
}

// The SDK under test is the same SNAPSHOT republished on every run: never serve it from Gradle's
// cache, or the gate would test a previous build.
configurations.configureEach {
    resolutionStrategy.cacheChangingModulesFor(0, "seconds")
}

dependencies {
    // Resolved as a real consumer would: by coordinate, from a repository. NOT by project(...).
    implementation("io.github.thanhng224:otp:$sdkVersion")
    implementation("io.github.thanhng224:otp-ui-compose:$sdkVersion")

    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")
}
