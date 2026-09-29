plugins { id("com.android.application") }
val sdkVersion = providers.gradleProperty("sdkVersion").get()
android {
    namespace = "io.github.thanhng224.consumer.logging"
    compileSdk = 37
    defaultConfig {
        applicationId = "io.github.thanhng224.consumer.logging"
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
}
kotlin { jvmToolchain(17) }
configurations.configureEach { resolutionStrategy.cacheChangingModulesFor(0, "seconds") }
dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:${providers.gradleProperty("sdkStdlibFloor").getOrElse("2.2.21")}")
    implementation(platform("io.github.thanhng224:bom:$sdkVersion"))
    implementation("io.github.thanhng224:event-logging")
    implementation("io.github.thanhng224:logging-file")
    implementation("io.github.thanhng224:event-logging-work")
}
