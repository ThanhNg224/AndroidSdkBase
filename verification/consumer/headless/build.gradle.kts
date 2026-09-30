plugins {
    id("com.android.application")
}

val sdkVersion = providers.gradleProperty("sdkVersion").get()

android {
    namespace = "io.github.thanhng224.consumer.headless"
    compileSdk = 37
    defaultConfig {
        applicationId = "io.github.thanhng224.consumer.headless"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // Reuse the Java-only SDK call sites; this directory contains no Compose code.
    sourceSets["main"].java.directories.add("../app/src/main/java")
}

kotlin { jvmToolchain(17) }

configurations.configureEach {
    resolutionStrategy.cacheChangingModulesFor(0, "seconds")
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:${providers.gradleProperty("sdkStdlibFloor").getOrElse("2.2.21")}")
    implementation(platform("io.github.thanhng224:bom:$sdkVersion"))
    implementation("io.github.thanhng224:otp")
    implementation("io.github.thanhng224:remote-config")
    testImplementation("junit:junit:4.13.2")
}
